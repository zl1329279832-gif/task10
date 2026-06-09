package com.campus.trade.service;

import com.campus.trade.common.config.TradeConfig;
import com.campus.trade.common.exception.BizException;
import com.campus.trade.common.exception.ErrorCode;
import com.campus.trade.common.util.DistributedLock;
import com.campus.trade.domain.entity.*;
import com.campus.trade.domain.entity.Order;
import com.campus.trade.domain.enums.*;
import com.campus.trade.dto.request.ArbitrationRequest;
import com.campus.trade.dto.response.SettlementResponse;
import com.campus.trade.mapper.*;
import com.campus.trade.service.impl.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Fund conservation integration tests covering the full lifecycle:
 * Payment SUCCESS -> Dispute Freeze -> Partial Arbitration -> Reversal -> Settlement Retry -> Duplicate Callback
 *
 * Verifies that frozenAmount + refundedAmount + sellerSettledAmount = paymentAmount at all times.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Fund Conservation - Full Lifecycle Integration")
class FundConservationIntegrationTest {

    // ── Shared mock infrastructure ──
    @Mock PaymentMapper paymentMapper;
    @Mock SettlementMapper settlementMapper;
    @Mock OrderMapper orderMapper;
    @Mock RefundMapper refundMapper;
    @Mock FundSplitMapper fundSplitMapper;
    @Mock ArbitrationMapper arbitrationMapper;
    @Mock DisputeMapper disputeMapper;
    @Mock AuditLogMapper auditLogMapper;
    @Mock AuditService auditService;
    @Mock OrderService orderService;
    @Mock DisputeService disputeService;
    @Mock DistributedLock distributedLock;
    @Mock TradeConfig tradeConfig;
    @Mock InventoryService inventoryService;
    @Mock SettlementService settlementService;
    @Mock EscrowService escrowService;
    @Mock FundSplitService fundSplitService;

    // ── Shared test data ──
    static final BigDecimal PAYMENT_AMOUNT = new BigDecimal("200.00");
    static final BigDecimal PARTIAL_REFUND = new BigDecimal("80.00");
    static final BigDecimal PARTIAL_SELLER = new BigDecimal("120.00");
    static final BigDecimal FEE_RATE = new BigDecimal("0.02");

    private Payment buildPayment(String status) {
        Payment p = new Payment();
        p.setId(1L); p.setPaymentNo("PAY001"); p.setOrderId(100L); p.setOrderNo("ORD001");
        p.setAmount(PAYMENT_AMOUNT); p.setStatus(status);
        return p;
    }

    private Order buildOrder() {
        Order o = new Order();
        o.setId(100L); o.setOrderNo("ORD001"); o.setBuyerId(10L); o.setSellerId(20L);
        o.setTotalAmount(PAYMENT_AMOUNT); o.setStatus(OrderStatus.DISPUTED.name());
        o.setPayTime(LocalDateTime.now());
        return o;
    }

    private Dispute buildDispute() {
        Dispute d = new Dispute();
        d.setId(1L); d.setDisputeNo("DSP001"); d.setOrderId(100L); d.setOrderNo("ORD001");
        d.setInitiatorId(10L); d.setRespondentId(20L);
        d.setStatus(DisputeStatus.ARBITRATING.name());
        return d;
    }

    private Settlement buildSettlement(String status) {
        Settlement s = new Settlement();
        s.setId(1L); s.setSettlementNo("STL001"); s.setOrderId(100L); s.setOrderNo("ORD001");
        s.setSellerId(20L); s.setOrderAmount(PAYMENT_AMOUNT);
        s.setPlatformFee(new BigDecimal("4.00")); s.setSettleAmount(new BigDecimal("196.00"));
        s.setEscrowAmount(PAYMENT_AMOUNT); s.setStatus(status); s.setRetryCount(0);
        return s;
    }

    private Refund buildRefund(String status, BigDecimal amount) {
        Refund r = new Refund();
        r.setId(10L); r.setRefundNo("REF001"); r.setOrderId(100L); r.setOrderNo("ORD001");
        r.setBuyerId(10L); r.setSellerId(20L); r.setRefundAmount(amount);
        r.setReason("Test"); r.setStatus(status);
        return r;
    }

    private ArbitrationRequest buildReq(String result, BigDecimal refundAmount, BigDecimal sellerAmount) {
        ArbitrationRequest r = new ArbitrationRequest();
        r.setDisputeId(1L); r.setResult(result); r.setDecision("Test decision");
        r.setRefundAmount(refundAmount); r.setSellerAmount(sellerAmount);
        return r;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Full chain: pay -> freeze -> partial arb -> reversal -> retry -> dup callback
    // ═══════════════════════════════════════════════════════════════
    @Nested @DisplayName("Full chain: payment -> dispute freeze -> partial arbitration -> reversal -> settlement retry -> duplicate callback")
    class FullChainTest {

        @Test @DisplayName("Step 1-2: Payment SUCCESS then dispute freezes funds")
        void paymentSuccessThenFreeze() {
            // Service under test: EscrowServiceImpl
            EscrowServiceImpl svc = new EscrowServiceImpl(
                    paymentMapper, settlementMapper, orderMapper, orderService,
                    settlementService, auditService, distributedLock, tradeConfig);

            Payment pmt = buildPayment(PaymentStatus.SUCCESS.name());
            Settlement stl = buildSettlement(SettlementStatus.PENDING.name());

            when(distributedLock.tryLock("order:100")).thenReturn(true);
            when(paymentMapper.findByOrderId(100L)).thenReturn(pmt);
            when(paymentMapper.freeze(1L, "SUCCESS", PAYMENT_AMOUNT, "Dispute raised")).thenReturn(1);
            when(settlementMapper.findByOrderId(100L)).thenReturn(stl);
            when(settlementMapper.freeze(1L, "PENDING", new BigDecimal("196.00"), "Dispute raised")).thenReturn(1);

            svc.freezeFunds(100L, 10L, "Dispute raised");

            verify(paymentMapper).freeze(1L, "SUCCESS", PAYMENT_AMOUNT, "Dispute raised");
            verify(settlementMapper).freeze(1L, "PENDING", new BigDecimal("196.00"), "Dispute raised");
            verify(auditService).logSync(eq(10L), isNull(), eq("ESCROW"), eq("FREEZE_PAYMENT"), eq("PAYMENT"), eq(1L), anyString());
            verify(auditService).logSync(eq(10L), isNull(), eq("ESCROW"), eq("FREEZE_SETTLEMENT"), eq("SETTLEMENT"), eq(1L), anyString());
        }

        @Test @DisplayName("Step 3: Partial arbitration splits funds correctly (conservation holds)")
        void partialArbitrationSplits() {
            FundSplitServiceImpl svc = new FundSplitServiceImpl(
                    fundSplitMapper, paymentMapper, refundMapper, settlementMapper,
                    orderMapper, auditService, distributedLock, tradeConfig);

            Payment pmt = buildPayment(PaymentStatus.FROZEN.name());
            Order ord = buildOrder();

            when(distributedLock.tryLock("fundsplit:1")).thenReturn(true);
            when(fundSplitMapper.findActiveByArbitrationId(1L)).thenReturn(null);
            when(paymentMapper.findById(1L)).thenReturn(pmt);
            when(tradeConfig.getPlatformFeeRate()).thenReturn(FEE_RATE);
            when(orderMapper.findById(100L)).thenReturn(ord);
            when(refundMapper.insert(any())).thenReturn(1);
            when(settlementMapper.insert(any())).thenReturn(1);
            when(fundSplitMapper.insert(any())).thenAnswer(inv -> {
                FundSplit fs = inv.getArgument(0); fs.setId(1L); return 1;
            });
            when(paymentMapper.unfreeze(1L, "SUCCESS")).thenReturn(1);
            when(fundSplitMapper.updateStatus(1L, "PENDING", "EXECUTED")).thenReturn(1);
            FundSplit executed = new FundSplit(); executed.setId(1L); executed.setStatus("EXECUTED");
            when(fundSplitMapper.findById(1L)).thenReturn(executed);

            // refund=80 + settle=120 = 200 (payment amount) — conservation holds
            FundSplit result = svc.executeFundSplit(1L, 100L, 1L, PARTIAL_REFUND, PARTIAL_SELLER, 99L);

            assertThat(result.getStatus()).isEqualTo("EXECUTED");
            verify(refundMapper).insert(argThat(r -> r.getRefundAmount().compareTo(PARTIAL_REFUND) == 0
                    && "SUCCESS".equals(r.getStatus())));
            verify(settlementMapper).insert(argThat(s -> s.getOrderAmount().compareTo(PARTIAL_SELLER) == 0
                    && "SETTLED".equals(s.getStatus())));
            verify(paymentMapper).unfreeze(1L, "SUCCESS"); // partial -> SUCCESS, not CLOSED
        }

        @Test @DisplayName("Step 4: Arbitration reversal cancels old split, reverses records, re-freezes, then re-splits")
        void arbitrationReversalFullCycle() {
            ArbitrationServiceImpl svc = new ArbitrationServiceImpl(
                    arbitrationMapper, disputeMapper, orderMapper, paymentMapper,
                    orderService, disputeService, fundSplitService, escrowService,
                    auditService, distributedLock, tradeConfig);

            Dispute d = buildDispute();
            d.setStatus(DisputeStatus.RESOLVED.name());
            Order o = buildOrder();
            Payment pmt = buildPayment(PaymentStatus.FROZEN.name());

            Arbitration oldArb = new Arbitration();
            oldArb.setId(1L); oldArb.setResult("PARTIAL");
            oldArb.setRefundAmount(PARTIAL_REFUND); oldArb.setSellerAmount(PARTIAL_SELLER);

            when(disputeMapper.findById(1L)).thenReturn(d);
            when(arbitrationMapper.findByDisputeId(1L)).thenReturn(oldArb);
            when(orderMapper.findById(100L)).thenReturn(o);
            when(paymentMapper.findByOrderId(100L)).thenReturn(pmt);
            when(distributedLock.tryLock("arbitrate:1")).thenReturn(true);
            when(arbitrationMapper.updateRuling(eq(1L), anyString(), anyString(), any(), any())).thenReturn(1);
            when(disputeMapper.updateStatus(1L, "RESOLVED", "ARBITRATING")).thenReturn(1);
            when(disputeMapper.updateStatus(1L, "ARBITRATING", "RESOLVED")).thenReturn(1);
            when(fundSplitService.executeFundSplit(anyLong(), anyLong(), anyLong(), any(), any(), anyLong()))
                    .thenReturn(new FundSplit());
            Arbitration updated = new Arbitration();
            updated.setId(1L); updated.setResult("BUYER_WIN");
            updated.setRefundAmount(PAYMENT_AMOUNT); updated.setSellerAmount(BigDecimal.ZERO);
            when(arbitrationMapper.findById(1L)).thenReturn(updated);

            // Reverse from PARTIAL(80/120) to BUYER_WIN(200/0)
            ArbitrationRequest req = buildReq("BUYER_WIN", PAYMENT_AMOUNT, null);
            Arbitration result = svc.reverseArbitration(99L, req);

            // Verify: old split cancelled (with orderId), new split executed
            verify(fundSplitService).cancelActiveSplit(1L, 100L, 99L);
            verify(fundSplitService).executeFundSplit(eq(1L), eq(100L), eq(1L),
                    eq(PAYMENT_AMOUNT), eq(BigDecimal.ZERO), eq(99L));
            // No separate unfreeze call
            verify(escrowService, never()).unfreezeFunds(anyLong(), anyLong(), anyString());
            verify(auditService).logSync(eq(99L), isNull(), eq("ARBITRATION"), eq("REVERSE"), eq("DISPUTE"), eq(1L), anyString());
            assertThat(result.getResult()).isEqualTo("BUYER_WIN");
        }

        @Test @DisplayName("Step 5: Settlement retry after FAILED")
        void settlementRetryAfterFailed() {
            SettlementServiceImpl svc = new SettlementServiceImpl(
                    settlementMapper, orderMapper, paymentMapper, orderService,
                    auditService, tradeConfig, distributedLock);

            Settlement stl = buildSettlement(SettlementStatus.FAILED.name());
            Settlement pending = buildSettlement(SettlementStatus.PENDING.name());
            Settlement settled = buildSettlement(SettlementStatus.SETTLED.name());
            Payment pmt = buildPayment(PaymentStatus.SUCCESS.name());

            when(distributedLock.tryLock("settlement:1")).thenReturn(true);
            when(settlementMapper.findById(1L)).thenReturn(stl, pending);
            when(settlementMapper.retryFailed(1L)).thenReturn(1);
            when(paymentMapper.findByOrderId(100L)).thenReturn(pmt);
            when(settlementMapper.updateStatus(1L, "PENDING", "SETTLED")).thenReturn(1);

            SettlementResponse result = svc.retrySettlement(1L);

            verify(settlementMapper).retryFailed(1L);
            verify(settlementMapper).updateStatus(1L, "PENDING", "SETTLED");
            verify(settlementMapper).updateSettledAt(eq(1L), any());
            verify(orderService).transitionOrder(100L, OrderStatus.SETTLED.name(), null, "Settlement completed");
        }

        @Test @DisplayName("Step 6: Duplicate payment callback rejected after SUCCESS")
        void duplicateCallbackRejected() {
            PaymentServiceImpl svc = new PaymentServiceImpl(
                    null, paymentMapper, orderMapper, orderService,
                    inventoryService, auditService, distributedLock);

            Payment pmt = buildPayment(PaymentStatus.PENDING.name());
            pmt.setPaymentNo("PAY001"); pmt.setOrderId(100L);
            Payment successPmt = buildPayment(PaymentStatus.SUCCESS.name());
            successPmt.setPaymentNo("PAY001"); successPmt.setOrderId(100L);

            Order ord = buildOrder();
            ord.setStatus(OrderStatus.CREATED.name());
            when(orderMapper.findByOrderNo("ORD001")).thenReturn(ord);
            when(paymentMapper.findByOrderNo("ORD001")).thenReturn(pmt);
            when(distributedLock.tryLock("order:100")).thenReturn(true);
            // Inside lock, re-read finds payment already SUCCESS (concurrent callback won)
            when(paymentMapper.findById(1L)).thenReturn(successPmt);

            String result = svc.simulatePayNotify("ORD001", "T001");

            // processSuccess detects already-SUCCESS inside lock, returns "success" without re-processing
            assertThat(result).isEqualTo("success");
            verify(paymentMapper, never()).updateStatus(anyLong(), anyString(), anyString());
        }

        @Test @DisplayName("Step 7: Lock contention on callback returns failure for Alipay retry")
        void lockContentionReturnsFailure() {
            PaymentServiceImpl svc = new PaymentServiceImpl(
                    null, paymentMapper, orderMapper, orderService,
                    inventoryService, auditService, distributedLock);

            Payment pmt = buildPayment(PaymentStatus.PENDING.name());
            pmt.setPaymentNo("PAY001"); pmt.setOrderId(100L);
            Order ord = buildOrder();
            ord.setStatus(OrderStatus.CREATED.name());

            when(orderMapper.findByOrderNo("ORD001")).thenReturn(ord);
            when(paymentMapper.findByOrderNo("ORD001")).thenReturn(pmt);
            when(distributedLock.tryLock("order:100")).thenReturn(false);

            String result = svc.simulatePayNotify("ORD001", "T001");

            assertThat(result).isEqualTo("failure");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Fund conservation invariant checks
    // ═══════════════════════════════════════════════════════════════
    @Nested @DisplayName("Fund conservation invariant checks")
    class ConservationInvariant {

        private FundSplitServiceImpl svc;

        @BeforeEach void setup() {
            svc = new FundSplitServiceImpl(
                    fundSplitMapper, paymentMapper, refundMapper, settlementMapper,
                    orderMapper, auditService, distributedLock, tradeConfig);
        }

        @Test @DisplayName("Reject split where refund + settle > payment amount")
        void rejectExcessiveAmounts() {
            Payment pmt = buildPayment(PaymentStatus.FROZEN.name());
            when(distributedLock.tryLock("fundsplit:1")).thenReturn(true);
            when(fundSplitMapper.findActiveByArbitrationId(1L)).thenReturn(null);
            when(paymentMapper.findById(1L)).thenReturn(pmt);

            // 150 + 100 = 250 > 200
            assertThatThrownBy(() -> svc.executeFundSplit(1L, 100L, 1L,
                    new BigDecimal("150.00"), new BigDecimal("100.00"), 99L))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("Conservation violated");
        }

        @Test @DisplayName("Reject split where refund + settle < payment amount")
        void rejectDeficientAmounts() {
            Payment pmt = buildPayment(PaymentStatus.FROZEN.name());
            when(distributedLock.tryLock("fundsplit:1")).thenReturn(true);
            when(fundSplitMapper.findActiveByArbitrationId(1L)).thenReturn(null);
            when(paymentMapper.findById(1L)).thenReturn(pmt);

            // 50 + 50 = 100 < 200
            assertThatThrownBy(() -> svc.executeFundSplit(1L, 100L, 1L,
                    new BigDecimal("50.00"), new BigDecimal("50.00"), 99L))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("Conservation violated");
        }

        @Test @DisplayName("Accept split where refund + settle == payment amount exactly")
        void acceptExactAmounts() {
            Payment pmt = buildPayment(PaymentStatus.FROZEN.name());
            Order ord = buildOrder();
            when(distributedLock.tryLock("fundsplit:1")).thenReturn(true);
            when(fundSplitMapper.findActiveByArbitrationId(1L)).thenReturn(null);
            when(paymentMapper.findById(1L)).thenReturn(pmt);
            when(tradeConfig.getPlatformFeeRate()).thenReturn(FEE_RATE);
            when(orderMapper.findById(100L)).thenReturn(ord);
            when(refundMapper.insert(any())).thenReturn(1);
            when(settlementMapper.insert(any())).thenReturn(1);
            when(fundSplitMapper.insert(any())).thenAnswer(inv -> {
                FundSplit fs = inv.getArgument(0); fs.setId(1L); return 1;
            });
            when(paymentMapper.unfreeze(1L, "SUCCESS")).thenReturn(1);
            when(fundSplitMapper.updateStatus(1L, "PENDING", "EXECUTED")).thenReturn(1);
            FundSplit executed = new FundSplit(); executed.setId(1L); executed.setStatus("EXECUTED");
            when(fundSplitMapper.findById(1L)).thenReturn(executed);

            // 80 + 120 = 200 — exact match
            FundSplit result = svc.executeFundSplit(1L, 100L, 1L,
                    PARTIAL_REFUND, PARTIAL_SELLER, 99L);
            assertThat(result.getStatus()).isEqualTo("EXECUTED");
        }

        @Test @DisplayName("Unfreeze failure rolls back transaction")
        void unfreezeFailureRollsBack() {
            Payment pmt = buildPayment(PaymentStatus.FROZEN.name());
            Order ord = buildOrder();
            when(distributedLock.tryLock("fundsplit:1")).thenReturn(true);
            when(fundSplitMapper.findActiveByArbitrationId(1L)).thenReturn(null);
            when(paymentMapper.findById(1L)).thenReturn(pmt);
            when(tradeConfig.getPlatformFeeRate()).thenReturn(FEE_RATE);
            when(orderMapper.findById(100L)).thenReturn(ord);
            when(refundMapper.insert(any())).thenReturn(1);
            when(settlementMapper.insert(any())).thenReturn(1);
            when(fundSplitMapper.insert(any())).thenAnswer(inv -> {
                FundSplit fs = inv.getArgument(0); fs.setId(1L); return 1;
            });
            // Unfreeze fails — returns 0
            when(paymentMapper.unfreeze(1L, "SUCCESS")).thenReturn(0);

            assertThatThrownBy(() -> svc.executeFundSplit(1L, 100L, 1L,
                    PARTIAL_REFUND, PARTIAL_SELLER, 99L))
                    .isInstanceOf(BizException.class);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  cancelActiveSplit reversal correctness
    // ═══════════════════════════════════════════════════════════════
    @Nested @DisplayName("cancelActiveSplit reversal correctness")
    class CancelSplitReversal {

        private FundSplitServiceImpl svc;

        @BeforeEach void setup() {
            svc = new FundSplitServiceImpl(
                    fundSplitMapper, paymentMapper, refundMapper, settlementMapper,
                    orderMapper, auditService, distributedLock, tradeConfig);
        }

        @Test @DisplayName("Cancel reverses refund SUCCESS -> REVERSED")
        void reversesRefund() {
            FundSplit active = new FundSplit();
            active.setId(1L); active.setStatus("EXECUTED"); active.setPaymentId(1L);
            active.setBuyerRefundNo("REF001"); active.setSellerSettlementNo(null);

            Refund refund = buildRefund("SUCCESS", PARTIAL_REFUND);
            Payment pmt = buildPayment("SUCCESS");

            when(fundSplitMapper.findActiveByArbitrationId(1L)).thenReturn(active);
            when(fundSplitMapper.updateStatus(1L, "EXECUTED", "CANCELLED")).thenReturn(1);
            when(refundMapper.findByRefundNo("REF001")).thenReturn(refund);
            when(refundMapper.updateStatus(10L, "SUCCESS", "REVERSED")).thenReturn(1);
            when(paymentMapper.findById(1L)).thenReturn(pmt);
            when(paymentMapper.freeze(eq(1L), eq("SUCCESS"), eq(PAYMENT_AMOUNT), anyString())).thenReturn(1);

            svc.cancelActiveSplit(1L, 100L, 99L);

            verify(refundMapper).updateStatus(10L, "SUCCESS", "REVERSED");
            verify(auditService).logSync(eq(99L), isNull(), eq("FUND_SPLIT"), eq("REVERSE_REFUND"),
                    eq("REFUND"), eq(10L), anyString());
        }

        @Test @DisplayName("Cancel reverses settlement SETTLED -> REVERSED")
        void reversesSettlement() {
            FundSplit active = new FundSplit();
            active.setId(1L); active.setStatus("EXECUTED"); active.setPaymentId(1L);
            active.setBuyerRefundNo(null); active.setSellerSettlementNo("STL001");

            Settlement settlement = buildSettlement("SETTLED");
            Payment pmt = buildPayment("SUCCESS");

            when(fundSplitMapper.findActiveByArbitrationId(1L)).thenReturn(active);
            when(fundSplitMapper.updateStatus(1L, "EXECUTED", "CANCELLED")).thenReturn(1);
            when(settlementMapper.findBySettlementNo("STL001")).thenReturn(settlement);
            when(settlementMapper.updateStatus(1L, "SETTLED", "REVERSED")).thenReturn(1);
            when(paymentMapper.findById(1L)).thenReturn(pmt);
            when(paymentMapper.freeze(eq(1L), eq("SUCCESS"), eq(PAYMENT_AMOUNT), anyString())).thenReturn(1);

            svc.cancelActiveSplit(1L, 100L, 99L);

            verify(settlementMapper).updateStatus(1L, "SETTLED", "REVERSED");
            verify(auditService).logSync(eq(99L), isNull(), eq("FUND_SPLIT"), eq("REVERSE_SETTLEMENT"),
                    eq("SETTLEMENT"), eq(1L), anyString());
        }

        @Test @DisplayName("Cancel re-freezes payment from CLOSED (full refund reversal)")
        void refreezesPaymentFromClosed() {
            FundSplit active = new FundSplit();
            active.setId(1L); active.setStatus("EXECUTED"); active.setPaymentId(1L);
            active.setBuyerRefundNo("REF001"); active.setSellerSettlementNo(null);

            Refund refund = buildRefund("SUCCESS", PAYMENT_AMOUNT);
            Payment pmt = buildPayment("CLOSED"); // was CLOSED after full refund

            when(fundSplitMapper.findActiveByArbitrationId(1L)).thenReturn(active);
            when(fundSplitMapper.updateStatus(1L, "EXECUTED", "CANCELLED")).thenReturn(1);
            when(refundMapper.findByRefundNo("REF001")).thenReturn(refund);
            when(refundMapper.updateStatus(10L, "SUCCESS", "REVERSED")).thenReturn(1);
            when(paymentMapper.findById(1L)).thenReturn(pmt);
            when(paymentMapper.freeze(1L, "CLOSED", PAYMENT_AMOUNT, "Arbitration reversal")).thenReturn(1);

            svc.cancelActiveSplit(1L, 100L, 99L);

            // CLOSED -> FROZEN (allowed by updated PaymentStateTransition)
            verify(paymentMapper).freeze(1L, "CLOSED", PAYMENT_AMOUNT, "Arbitration reversal");
            verify(auditService).logSync(eq(99L), isNull(), eq("FUND_SPLIT"), eq("REFREEZE_PAYMENT"),
                    eq("PAYMENT"), eq(1L), anyString());
        }

        @Test @DisplayName("Cancel skips already-FROZEN payment")
        void skipsAlreadyFrozenPayment() {
            FundSplit active = new FundSplit();
            active.setId(1L); active.setStatus("EXECUTED"); active.setPaymentId(1L);
            active.setBuyerRefundNo(null); active.setSellerSettlementNo(null);

            Payment pmt = buildPayment("FROZEN");

            when(fundSplitMapper.findActiveByArbitrationId(1L)).thenReturn(active);
            when(fundSplitMapper.updateStatus(1L, "EXECUTED", "CANCELLED")).thenReturn(1);
            when(paymentMapper.findById(1L)).thenReturn(pmt);

            svc.cancelActiveSplit(1L, 100L, 99L);

            verify(paymentMapper, never()).freeze(anyLong(), anyString(), any(), anyString());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Refund fund release
    // ═══════════════════════════════════════════════════════════════
    @Nested @DisplayName("Refund fund release")
    class RefundFundRelease {

        private RefundServiceImpl svc;

        @BeforeEach void setup() {
            svc = new RefundServiceImpl(
                    refundMapper, orderMapper, paymentMapper, orderService,
                    auditService, distributedLock, escrowService);
        }

        @Test @DisplayName("approveRefund transitions to SUCCESS and closes FROZEN payment")
        void approveUnfreezesAndCloses() {
            Refund r = buildRefund("PENDING", PAYMENT_AMOUNT);
            Payment pmt = buildPayment(PaymentStatus.FROZEN.name());

            when(refundMapper.findById(10L)).thenReturn(r);
            when(distributedLock.tryLock("refund:10")).thenReturn(true);
            when(refundMapper.updateStatus(10L, "PENDING", "APPROVED")).thenReturn(1);
            when(refundMapper.updateStatus(10L, "APPROVED", "SUCCESS")).thenReturn(1);
            when(paymentMapper.findByOrderId(100L)).thenReturn(pmt);
            when(paymentMapper.unfreeze(1L, "CLOSED")).thenReturn(1);
            Refund approved = buildRefund("SUCCESS", PAYMENT_AMOUNT);
            when(refundMapper.findById(10L)).thenReturn(r, approved);

            svc.approveRefund(20L, 10L);

            InOrder inOrder = inOrder(refundMapper, paymentMapper, orderService);
            inOrder.verify(refundMapper).updateStatus(10L, "PENDING", "APPROVED");
            inOrder.verify(refundMapper).updateStatus(10L, "APPROVED", "SUCCESS");
            inOrder.verify(paymentMapper).unfreeze(1L, "CLOSED");
            inOrder.verify(orderService).transitionOrder(100L, OrderStatus.REFUNDED.name(), 20L, "Refund approved");
        }

        @Test @DisplayName("approveRefund closes SUCCESS payment (not frozen)")
        void approveClosesSuccessPayment() {
            Refund r = buildRefund("PENDING", PAYMENT_AMOUNT);
            Payment pmt = buildPayment(PaymentStatus.SUCCESS.name());

            when(refundMapper.findById(10L)).thenReturn(r);
            when(distributedLock.tryLock("refund:10")).thenReturn(true);
            when(refundMapper.updateStatus(10L, "PENDING", "APPROVED")).thenReturn(1);
            when(refundMapper.updateStatus(10L, "APPROVED", "SUCCESS")).thenReturn(1);
            when(paymentMapper.findByOrderId(100L)).thenReturn(pmt);
            when(paymentMapper.updateStatus(1L, "SUCCESS", "CLOSED")).thenReturn(1);
            Refund approved = buildRefund("SUCCESS", PAYMENT_AMOUNT);
            when(refundMapper.findById(10L)).thenReturn(r, approved);

            svc.approveRefund(20L, 10L);

            verify(paymentMapper).updateStatus(1L, "SUCCESS", "CLOSED");
        }

        @Test @DisplayName("rejectRefund unfreezes funds")
        void rejectUnfreezes() {
            Refund r = buildRefund("PENDING", PAYMENT_AMOUNT);

            when(refundMapper.findById(10L)).thenReturn(r);
            when(distributedLock.tryLock("refund:10")).thenReturn(true);
            when(refundMapper.updateStatus(10L, "PENDING", "REJECTED")).thenReturn(1);
            Refund rejected = buildRefund("REJECTED", PAYMENT_AMOUNT);
            when(refundMapper.findById(10L)).thenReturn(r, rejected);

            svc.rejectRefund(20L, 10L, "Insufficient evidence");

            verify(orderService).transitionOrder(100L, OrderStatus.PAID.name(), 20L,
                    "Refund rejected: Insufficient evidence");
            verify(escrowService).unfreezeFunds(100L, 20L, "Refund rejected: Insufficient evidence");
        }

        @Test @DisplayName("rejectRefund logs when unfreeze fails but does not throw")
        void rejectLogsUnfreezeFailure() {
            Refund r = buildRefund("PENDING", PAYMENT_AMOUNT);

            when(refundMapper.findById(10L)).thenReturn(r);
            when(distributedLock.tryLock("refund:10")).thenReturn(true);
            when(refundMapper.updateStatus(10L, "PENDING", "REJECTED")).thenReturn(1);
            doThrow(new BizException(ErrorCode.PAYMENT_UNFREEZE_FAILED))
                    .when(escrowService).unfreezeFunds(anyLong(), anyLong(), anyString());
            Refund rejected = buildRefund("REJECTED", PAYMENT_AMOUNT);
            when(refundMapper.findById(10L)).thenReturn(r, rejected);

            // Should not throw despite unfreeze failure
            assertThatCode(() -> svc.rejectRefund(20L, 10L, "No"))
                    .doesNotThrowAnyException();

            verify(auditService).logSync(eq(20L), isNull(), eq("REFUND"),
                    eq("UNFREEZE_FAILED_ON_REJECT"), eq("REFUND"), eq(10L), anyString());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  SELLER_WIN no double-unfreeze
    // ═══════════════════════════════════════════════════════════════
    @Nested @DisplayName("SELLER_WIN no double-unfreeze")
    class SellerWinNoDoubleUnfreeze {

        @Test @DisplayName("SELLER_WIN arbitration does not call unfreezeFunds separately")
        void noSeparateUnfreeze() {
            ArbitrationServiceImpl svc = new ArbitrationServiceImpl(
                    arbitrationMapper, disputeMapper, orderMapper, paymentMapper,
                    orderService, disputeService, fundSplitService, escrowService,
                    auditService, distributedLock, tradeConfig);

            Dispute d = buildDispute();
            Order o = buildOrder();
            Payment pmt = buildPayment(PaymentStatus.FROZEN.name());

            when(disputeMapper.findById(1L)).thenReturn(d);
            when(arbitrationMapper.findByDisputeId(1L)).thenReturn(null);
            when(orderMapper.findById(100L)).thenReturn(o);
            when(paymentMapper.findByOrderId(100L)).thenReturn(pmt);
            when(distributedLock.tryLock("arbitrate:1")).thenReturn(true);
            when(arbitrationMapper.insert(any())).thenAnswer(inv -> {
                Arbitration a = inv.getArgument(0); a.setId(1L); return 1;
            });
            when(disputeMapper.updateStatus(1L, "ARBITRATING", "RESOLVED")).thenReturn(1);
            when(fundSplitService.executeFundSplit(anyLong(), eq(100L), eq(1L),
                    eq(BigDecimal.ZERO), eq(PAYMENT_AMOUNT), eq(99L))).thenReturn(new FundSplit());

            svc.arbitrate(99L, buildReq("SELLER_WIN", null, null));

            // Key assertion: escrowService.unfreezeFunds is NEVER called
            verify(escrowService, never()).unfreezeFunds(anyLong(), anyLong(), anyString());
            // Only executeFundSplit handles the unfreeze internally
            verify(fundSplitService).executeFundSplit(anyLong(), eq(100L), eq(1L),
                    eq(BigDecimal.ZERO), eq(PAYMENT_AMOUNT), eq(99L));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Settlement distributed lock coverage
    // ═══════════════════════════════════════════════════════════════
    @Nested @DisplayName("Settlement distributed lock coverage")
    class SettlementLocking {

        private SettlementServiceImpl svc;

        @BeforeEach void setup() {
            svc = new SettlementServiceImpl(
                    settlementMapper, orderMapper, paymentMapper, orderService,
                    auditService, tradeConfig, distributedLock);
        }

        @Test @DisplayName("createSettlement acquires order lock")
        void createAcquiresLock() {
            when(distributedLock.tryLock("order:100")).thenReturn(true);
            Order o = buildOrder();
            o.setStatus(OrderStatus.RECEIVED.name());
            when(orderMapper.findById(100L)).thenReturn(o);
            when(settlementMapper.findByOrderId(100L)).thenReturn(null);
            Payment pmt = buildPayment(PaymentStatus.SUCCESS.name());
            when(paymentMapper.findByOrderId(100L)).thenReturn(pmt);
            when(tradeConfig.getPlatformFeeRate()).thenReturn(FEE_RATE);
            when(settlementMapper.insert(any())).thenReturn(1);

            svc.createSettlement(100L);

            verify(distributedLock).tryLock("order:100");
            verify(distributedLock).unlock("order:100");
        }

        @Test @DisplayName("createSettlement fails on lock contention")
        void createFailsOnLockContention() {
            when(distributedLock.tryLock("order:100")).thenReturn(false);

            assertThatThrownBy(() -> svc.createSettlement(100L))
                    .isInstanceOf(BizException.class);
        }

        @Test @DisplayName("executeSettlement acquires settlement lock")
        void executeAcquiresLock() {
            when(distributedLock.tryLock("settlement:1")).thenReturn(true);
            Settlement stl = buildSettlement(SettlementStatus.PENDING.name());
            Payment pmt = buildPayment(PaymentStatus.SUCCESS.name());
            when(settlementMapper.findById(1L)).thenReturn(stl);
            when(paymentMapper.findByOrderId(100L)).thenReturn(pmt);
            when(settlementMapper.updateStatus(1L, "PENDING", "SETTLED")).thenReturn(1);

            svc.executeSettlement(1L);

            verify(distributedLock).tryLock("settlement:1");
            verify(distributedLock).unlock("settlement:1");
        }

        @Test @DisplayName("retrySettlement acquires lock before reading")
        void retryLocksBeforeRead() {
            when(distributedLock.tryLock("settlement:1")).thenReturn(true);
            Settlement stl = buildSettlement(SettlementStatus.FAILED.name());
            Settlement pending = buildSettlement(SettlementStatus.PENDING.name());
            Payment pmt = buildPayment(PaymentStatus.SUCCESS.name());
            when(settlementMapper.findById(1L)).thenReturn(stl, pending);
            when(settlementMapper.retryFailed(1L)).thenReturn(1);
            when(paymentMapper.findByOrderId(100L)).thenReturn(pmt);
            when(settlementMapper.updateStatus(1L, "PENDING", "SETTLED")).thenReturn(1);

            svc.retrySettlement(1L);

            InOrder inOrder = inOrder(distributedLock, settlementMapper);
            inOrder.verify(distributedLock).tryLock("settlement:1");
            inOrder.verify(settlementMapper).findById(1L); // read INSIDE lock
            inOrder.verify(settlementMapper).retryFailed(1L);
        }

        @Test @DisplayName("retrySettlement rejects frozen payment")
        void retryRejectsFrozenPayment() {
            when(distributedLock.tryLock("settlement:1")).thenReturn(true);
            Settlement stl = buildSettlement(SettlementStatus.FAILED.name());
            Settlement pending = buildSettlement(SettlementStatus.PENDING.name());
            Payment pmt = buildPayment(PaymentStatus.FROZEN.name());
            when(settlementMapper.findById(1L)).thenReturn(stl, pending);
            when(settlementMapper.retryFailed(1L)).thenReturn(1);
            when(paymentMapper.findByOrderId(100L)).thenReturn(pmt);

            // doExecuteSettlement checks payment frozen
            assertThatThrownBy(() -> svc.retrySettlement(1L))
                    .isInstanceOf(BizException.class);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Audit log ordering for fund operations
    // ═══════════════════════════════════════════════════════════════
    @Nested @DisplayName("Audit log ordering for fund operations")
    class AuditOrdering {

        @Test @DisplayName("Fund split uses synchronous audit logging")
        void fundSplitUsesSyncAudit() {
            FundSplitServiceImpl svc = new FundSplitServiceImpl(
                    fundSplitMapper, paymentMapper, refundMapper, settlementMapper,
                    orderMapper, auditService, distributedLock, tradeConfig);

            Payment pmt = buildPayment(PaymentStatus.FROZEN.name());
            Order ord = buildOrder();
            when(distributedLock.tryLock("fundsplit:1")).thenReturn(true);
            when(fundSplitMapper.findActiveByArbitrationId(1L)).thenReturn(null);
            when(paymentMapper.findById(1L)).thenReturn(pmt);
            when(tradeConfig.getPlatformFeeRate()).thenReturn(FEE_RATE);
            when(orderMapper.findById(100L)).thenReturn(ord);
            when(settlementMapper.insert(any())).thenReturn(1);
            when(fundSplitMapper.insert(any())).thenAnswer(inv -> {
                FundSplit fs = inv.getArgument(0); fs.setId(1L); return 1;
            });
            when(paymentMapper.unfreeze(1L, "SUCCESS")).thenReturn(1);
            when(fundSplitMapper.updateStatus(1L, "PENDING", "EXECUTED")).thenReturn(1);
            FundSplit executed = new FundSplit(); executed.setId(1L); executed.setStatus("EXECUTED");
            when(fundSplitMapper.findById(1L)).thenReturn(executed);

            svc.executeFundSplit(1L, 100L, 1L, BigDecimal.ZERO, PAYMENT_AMOUNT, 99L);

            // Must use logSync, NOT log
            verify(auditService).logSync(eq(99L), isNull(), eq("FUND_SPLIT"), eq("EXECUTE"),
                    eq("ORDER"), eq(100L), anyString());
            verify(auditService, never()).log(anyLong(), anyString(), eq("FUND_SPLIT"),
                    eq("EXECUTE"), anyString(), anyLong(), anyString());
        }

        @Test @DisplayName("Escrow freeze uses synchronous audit logging")
        void escrowFreezeUsesSyncAudit() {
            EscrowServiceImpl svc = new EscrowServiceImpl(
                    paymentMapper, settlementMapper, orderMapper, orderService,
                    settlementService, auditService, distributedLock, tradeConfig);

            Payment pmt = buildPayment(PaymentStatus.SUCCESS.name());
            when(distributedLock.tryLock("order:100")).thenReturn(true);
            when(paymentMapper.findByOrderId(100L)).thenReturn(pmt);
            when(paymentMapper.freeze(1L, "SUCCESS", PAYMENT_AMOUNT, "test")).thenReturn(1);
            when(settlementMapper.findByOrderId(100L)).thenReturn(null);

            svc.freezeFunds(100L, 10L, "test");

            verify(auditService).logSync(eq(10L), isNull(), eq("ESCROW"), eq("FREEZE_PAYMENT"),
                    eq("PAYMENT"), eq(1L), anyString());
        }
    }
}
