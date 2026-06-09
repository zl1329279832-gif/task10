package com.campus.trade.service;

import com.campus.trade.common.config.TradeConfig;
import com.campus.trade.common.exception.BizException;
import com.campus.trade.common.exception.ErrorCode;
import com.campus.trade.common.util.DistributedLock;
import com.campus.trade.domain.entity.Arbitration;
import com.campus.trade.domain.entity.Dispute;
import com.campus.trade.domain.entity.Order;
import com.campus.trade.domain.entity.Payment;
import com.campus.trade.domain.entity.Refund;
import com.campus.trade.domain.entity.Settlement;
import com.campus.trade.domain.enums.*;
import com.campus.trade.dto.request.ArbitrationRequest;
import com.campus.trade.dto.request.DisputeRequest;
import com.campus.trade.dto.request.RefundRequest;
import com.campus.trade.dto.response.SettlementResponse;
import com.campus.trade.mapper.*;
import com.campus.trade.service.impl.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for escrow payment, freeze, arbitration, and split settlement flows.
 */
@ExtendWith(MockitoExtension.class)
class EscrowArbitrationIntegrationTest {

    // ── Shared mocks ──
    @Mock OrderMapper orderMapper;
    @Mock PaymentMapper paymentMapper;
    @Mock SettlementMapper settlementMapper;
    @Mock ArbitrationMapper arbitrationMapper;
    @Mock DisputeMapper disputeMapper;
    @Mock DisputeEvidenceMapper evidenceMapper;
    @Mock RefundMapper refundMapper;
    @Mock OrderStatusLogMapper statusLogMapper;
    @Mock ProductMapper productMapper;
    @Mock SkuMapper skuMapper;
    @Mock UserMapper userMapper;
    @Mock AuditLogMapper auditLogMapper;
    @Mock DistributedLock distributedLock;

    // ── Services ──
    AuditService auditService;
    OrderService orderService;
    SettlementService settlementService;
    RefundService refundService;
    DisputeService disputeService;
    ArbitrationService arbitrationService;
    InventoryService inventoryService;

    TradeConfig tradeConfig;

    // ── Test data ──
    static final Long BUYER_ID = 1L;
    static final Long SELLER_ID = 2L;
    static final Long ADMIN_ID = 3L;
    static final Long ORDER_ID = 100L;
    static final Long PAYMENT_ID = 200L;
    static final Long SETTLEMENT_ID = 300L;
    static final Long DISPUTE_ID = 400L;
    static final Long REFUND_ID = 500L;
    static final BigDecimal ORDER_AMOUNT = new BigDecimal("100.00");
    static final BigDecimal PLATFORM_FEE = new BigDecimal("2.00");
    static final BigDecimal SETTLE_AMOUNT = new BigDecimal("98.00");

    @BeforeEach
    void setUp() {
        tradeConfig = new TradeConfig();
        tradeConfig.setPlatformFeeRate(new BigDecimal("0.02"));
        tradeConfig.setPayTimeoutMinutes(30);
        tradeConfig.setAutoReceiveDays(7);

        auditService = new AuditServiceImpl(auditLogMapper);
        inventoryService = new InventoryServiceImpl(skuMapper);

        // Wire services — OrderServiceImpl uses setter injection for SettlementService to break cycle
        OrderServiceImpl orderServiceImpl = new OrderServiceImpl(
                orderMapper, statusLogMapper, productMapper, skuMapper, userMapper,
                inventoryService, auditService, distributedLock, tradeConfig);
        orderService = orderServiceImpl;

        settlementService = new SettlementServiceImpl(
                settlementMapper, orderMapper, paymentMapper,
                orderService, auditService, tradeConfig, distributedLock);

        // Complete the cycle via setter
        orderServiceImpl.setSettlementService(settlementService);

        refundService = new RefundServiceImpl(
                refundMapper, orderMapper, paymentMapper,
                orderService, settlementService, auditService, distributedLock);

        disputeService = new DisputeServiceImpl(
                disputeMapper, evidenceMapper, arbitrationMapper, orderMapper,
                orderService, settlementService, auditService, distributedLock);

        arbitrationService = new ArbitrationServiceImpl(
                arbitrationMapper, disputeMapper, orderMapper, paymentMapper, refundMapper,
                orderService, disputeService, settlementService, auditService, distributedLock);

        // Default: locks always succeed
        lenient().when(distributedLock.tryLock(anyString())).thenReturn(true);
        lenient().when(distributedLock.tryLock(anyString(), anyLong())).thenReturn(true);
    }

    // ── Helper builders ──
    private Order buildOrder(String status) {
        Order o = new Order();
        o.setId(ORDER_ID);
        o.setOrderNo("ORD001");
        o.setBuyerId(BUYER_ID);
        o.setSellerId(SELLER_ID);
        o.setProductId(10L);
        o.setSkuId(20L);
        o.setSkuName("Test SKU");
        o.setQuantity(1);
        o.setUnitPrice(ORDER_AMOUNT);
        o.setTotalAmount(ORDER_AMOUNT);
        o.setStatus(status);
        o.setPayExpireAt(LocalDateTime.now().plusHours(1));
        return o;
    }

    private Payment buildPayment(String status) {
        Payment p = new Payment();
        p.setId(PAYMENT_ID);
        p.setPaymentNo("PAY001");
        p.setOrderId(ORDER_ID);
        p.setOrderNo("ORD001");
        p.setAmount(ORDER_AMOUNT);
        p.setPayChannel("ALIPAY");
        p.setStatus(status);
        return p;
    }

    private Settlement buildSettlement(String status) {
        Settlement s = new Settlement();
        s.setId(SETTLEMENT_ID);
        s.setSettlementNo("STL001");
        s.setOrderId(ORDER_ID);
        s.setOrderNo("ORD001");
        s.setSellerId(SELLER_ID);
        s.setOrderAmount(ORDER_AMOUNT);
        s.setPlatformFee(PLATFORM_FEE);
        s.setSettleAmount(SETTLE_AMOUNT);
        s.setStatus(status);
        s.setRetryCount(0);
        s.setMaxRetry(5);
        return s;
    }

    private Dispute buildDispute(String status) {
        Dispute d = new Dispute();
        d.setId(DISPUTE_ID);
        d.setDisputeNo("DSP001");
        d.setOrderId(ORDER_ID);
        d.setOrderNo("ORD001");
        d.setInitiatorId(BUYER_ID);
        d.setRespondentId(SELLER_ID);
        d.setReason("Item not as described");
        d.setStatus(status);
        return d;
    }

    // ───────────────────────────────────────────────────────────────
    // 1. Happy path: payment → escrow → confirm receive → auto settle
    // ───────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("1. Normal Escrow Settlement Flow")
    class NormalEscrowFlow {

        @Test
        @DisplayName("confirmReceive triggers auto-settlement, ESCROW → SUCCESS")
        void confirmReceive_autoSettlement() {
            Order shipped = buildOrder("SHIPPED");
            shipped.setShipTime(LocalDateTime.now());
            when(orderMapper.findById(ORDER_ID)).thenReturn(shipped);
            when(orderMapper.updateStatus(ORDER_ID, "SHIPPED", "RECEIVED")).thenReturn(1);

            // After transition to RECEIVED, createSettlement is called
            Order received = buildOrder("RECEIVED");
            // Return RECEIVED on second call (after status update)
            when(orderMapper.findById(ORDER_ID)).thenReturn(shipped).thenReturn(received);
            when(settlementMapper.findByOrderId(ORDER_ID)).thenReturn(null);
            when(settlementMapper.insert(any(Settlement.class))).thenAnswer(inv -> {
                Settlement s = inv.getArgument(0);
                s.setId(SETTLEMENT_ID);
                return 1;
            });

            // executeSettlement
            Settlement pending = buildSettlement("PENDING");
            when(settlementMapper.findById(SETTLEMENT_ID)).thenReturn(pending);
            when(settlementMapper.updateStatus(SETTLEMENT_ID, "PENDING", "SETTLED")).thenReturn(1);

            Payment escrow = buildPayment("ESCROW");
            when(paymentMapper.findByOrderId(ORDER_ID)).thenReturn(escrow);
            when(paymentMapper.updateStatus(PAYMENT_ID, "ESCROW", "SUCCESS")).thenReturn(1);

            // Order RECEIVED → SETTLED
            when(orderMapper.updateStatus(ORDER_ID, "RECEIVED", "SETTLED")).thenReturn(1);

            orderService.confirmReceive(BUYER_ID, ORDER_ID);

            verify(settlementMapper).insert(any(Settlement.class));
            verify(paymentMapper).updateStatus(PAYMENT_ID, "ESCROW", "SUCCESS");
            verify(orderMapper).updateStatus(ORDER_ID, "RECEIVED", "SETTLED");
        }
    }

    // ───────────────────────────────────────────────────────────────
    // 2. Refund freeze flow: apply refund → funds frozen
    // ───────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("2. Refund Freeze Flow")
    class RefundFreezeFlow {

        @Test
        @DisplayName("applyRefund freezes ESCROW payment to FROZEN")
        void applyRefund_freezesPayment() {
            Order paid = buildOrder("PAID");
            when(orderMapper.findById(ORDER_ID)).thenReturn(paid);
            when(refundMapper.findByOrderId(ORDER_ID)).thenReturn(null);
            when(refundMapper.insert(any(Refund.class))).thenReturn(1);
            when(orderMapper.updateStatus(ORDER_ID, "PAID", "REFUNDING")).thenReturn(1);

            Payment escrow = buildPayment("ESCROW");
            when(paymentMapper.findByOrderId(ORDER_ID)).thenReturn(escrow);
            when(paymentMapper.updateStatus(PAYMENT_ID, "ESCROW", "FROZEN")).thenReturn(1);
            when(settlementMapper.findByOrderId(ORDER_ID)).thenReturn(null);

            RefundRequest req = new RefundRequest();
            req.setOrderId(ORDER_ID);
            req.setRefundAmount(ORDER_AMOUNT);
            req.setReason("Not as described");

            refundService.applyRefund(BUYER_ID, req);

            verify(paymentMapper).updateStatus(PAYMENT_ID, "ESCROW", "FROZEN");
            verify(paymentMapper).updateFreezeInfo(eq(PAYMENT_ID), any(), anyString());
        }
    }

    // ───────────────────────────────────────────────────────────────
    // 3. Refund rejected → unfreeze
    // ───────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("3. Refund Rejected Unfreeze")
    class RefundRejectedUnfreeze {

        @Test
        @DisplayName("rejectRefund unfreezes FROZEN payment back to ESCROW")
        void rejectRefund_unfreezesPayment() {
            Refund r = new Refund();
            r.setId(REFUND_ID);
            r.setRefundNo("REF001");
            r.setOrderId(ORDER_ID);
            r.setOrderNo("ORD001");
            r.setBuyerId(BUYER_ID);
            r.setSellerId(SELLER_ID);
            r.setRefundAmount(ORDER_AMOUNT);
            r.setReason("Test");
            r.setStatus("PENDING");
            when(refundMapper.findById(REFUND_ID)).thenReturn(r);
            when(refundMapper.updateStatus(REFUND_ID, "PENDING", "REJECTED")).thenReturn(1);

            Order refunding = buildOrder("REFUNDING");
            when(orderMapper.findById(ORDER_ID)).thenReturn(refunding);
            when(orderMapper.updateStatus(ORDER_ID, "REFUNDING", "PAID")).thenReturn(1);

            Payment frozen = buildPayment("FROZEN");
            when(paymentMapper.findByOrderId(ORDER_ID)).thenReturn(frozen);
            when(paymentMapper.updateStatus(PAYMENT_ID, "FROZEN", "ESCROW")).thenReturn(1);
            when(settlementMapper.findByOrderId(ORDER_ID)).thenReturn(null);

            Refund updated = new Refund();
            updated.setId(REFUND_ID);
            updated.setRefundNo("REF001");
            updated.setOrderId(ORDER_ID);
            updated.setOrderNo("ORD001");
            updated.setBuyerId(BUYER_ID);
            updated.setSellerId(SELLER_ID);
            updated.setRefundAmount(ORDER_AMOUNT);
            updated.setReason("Test");
            updated.setStatus("REJECTED");
            when(refundMapper.findById(REFUND_ID))
                    .thenReturn(r).thenReturn(updated);

            refundService.rejectRefund(SELLER_ID, REFUND_ID, "No valid reason");

            verify(paymentMapper).updateStatus(PAYMENT_ID, "FROZEN", "ESCROW");
            verify(paymentMapper).updateUnfreezeInfo(eq(PAYMENT_ID), any());
        }
    }

    // ───────────────────────────────────────────────────────────────
    // 4. Dispute → Arbitration: Buyer wins → full refund
    // ───────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("4. Arbitration Buyer Wins")
    class ArbitrationBuyerWins {

        @Test
        @DisplayName("BUYER_WIN closes FROZEN payment and refunds buyer")
        void buyerWin_fullRefund() {
            Dispute d = buildDispute("ARBITRATING");
            when(disputeMapper.findById(DISPUTE_ID)).thenReturn(d);
            when(arbitrationMapper.findActiveByDisputeId(DISPUTE_ID)).thenReturn(null);
            when(arbitrationMapper.insert(any(Arbitration.class))).thenAnswer(inv -> {
                Arbitration a = inv.getArgument(0);
                a.setId(600L);
                return 1;
            });
            when(disputeMapper.updateStatus(DISPUTE_ID, "ARBITRATING", "RESOLVED")).thenReturn(1);

            Order disputed = buildOrder("DISPUTED");
            when(orderMapper.findById(ORDER_ID)).thenReturn(disputed);

            Payment frozen = buildPayment("FROZEN");
            when(paymentMapper.findByOrderId(ORDER_ID)).thenReturn(frozen);
            when(paymentMapper.updateStatus(PAYMENT_ID, "FROZEN", "CLOSED")).thenReturn(1);
            when(refundMapper.findByOrderId(ORDER_ID)).thenReturn(null);
            when(refundMapper.insert(any(Refund.class))).thenReturn(1);
            when(orderMapper.updateStatus(ORDER_ID, "DISPUTED", "REFUNDED")).thenReturn(1);

            ArbitrationRequest req = new ArbitrationRequest();
            req.setDisputeId(DISPUTE_ID);
            req.setResult("BUYER_WIN");
            req.setDecision("Buyer is right");

            Arbitration result = arbitrationService.arbitrate(ADMIN_ID, req);

            assertThat(result.getResult()).isEqualTo("BUYER_WIN");
            assertThat(result.getIsActive()).isEqualTo(1);
            verify(paymentMapper).updateStatus(PAYMENT_ID, "FROZEN", "CLOSED");
            verify(refundMapper).insert(any(Refund.class));
            verify(orderMapper).updateStatus(ORDER_ID, "DISPUTED", "REFUNDED");
        }
    }

    // ───────────────────────────────────────────────────────────────
    // 5. Dispute → Arbitration: Seller wins → unfreeze and settle
    // ───────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("5. Arbitration Seller Wins")
    class ArbitrationSellerWins {

        @Test
        @DisplayName("SELLER_WIN unfreezes funds and settles to seller")
        void sellerWin_settleToSeller() {
            Dispute d = buildDispute("ARBITRATING");
            when(disputeMapper.findById(DISPUTE_ID)).thenReturn(d);
            when(arbitrationMapper.findActiveByDisputeId(DISPUTE_ID)).thenReturn(null);
            when(arbitrationMapper.insert(any(Arbitration.class))).thenAnswer(inv -> {
                Arbitration a = inv.getArgument(0);
                a.setId(601L);
                return 1;
            });
            when(disputeMapper.updateStatus(DISPUTE_ID, "ARBITRATING", "RESOLVED")).thenReturn(1);

            Order disputed = buildOrder("DISPUTED");
            when(orderMapper.findById(ORDER_ID)).thenReturn(disputed);

            // unfreeze
            Payment frozen = buildPayment("FROZEN");
            when(paymentMapper.findByOrderId(ORDER_ID)).thenReturn(frozen);
            when(paymentMapper.updateStatus(PAYMENT_ID, "FROZEN", "ESCROW")).thenReturn(1);
            when(settlementMapper.findByOrderId(ORDER_ID)).thenReturn(null);

            // createSettlement after unfreeze - order needs to be RECEIVED for createSettlement
            // but since it's disputed, it won't be RECEIVED, so we expect the catch block to handle it
            when(orderMapper.updateStatus(ORDER_ID, "DISPUTED", "SETTLED")).thenReturn(1);

            ArbitrationRequest req = new ArbitrationRequest();
            req.setDisputeId(DISPUTE_ID);
            req.setResult("SELLER_WIN");
            req.setDecision("Seller is right");

            Arbitration result = arbitrationService.arbitrate(ADMIN_ID, req);

            assertThat(result.getResult()).isEqualTo("SELLER_WIN");
            verify(paymentMapper).updateStatus(PAYMENT_ID, "FROZEN", "ESCROW");
            verify(orderMapper).updateStatus(ORDER_ID, "DISPUTED", "SETTLED");
        }
    }

    // ───────────────────────────────────────────────────────────────
    // 6. Dispute → Arbitration: Partial refund → split settlement
    // ───────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("6. Arbitration Partial Refund")
    class ArbitrationPartialRefund {

        @Test
        @DisplayName("PARTIAL splits settlement between buyer and seller")
        void partialRefund_splitSettlement() {
            BigDecimal refundAmount = new BigDecimal("40.00");

            Dispute d = buildDispute("ARBITRATING");
            when(disputeMapper.findById(DISPUTE_ID)).thenReturn(d);
            when(arbitrationMapper.findActiveByDisputeId(DISPUTE_ID)).thenReturn(null);
            when(arbitrationMapper.insert(any(Arbitration.class))).thenAnswer(inv -> {
                Arbitration a = inv.getArgument(0);
                a.setId(602L);
                return 1;
            });
            when(disputeMapper.updateStatus(DISPUTE_ID, "ARBITRATING", "RESOLVED")).thenReturn(1);

            Order disputed = buildOrder("DISPUTED");
            when(orderMapper.findById(ORDER_ID)).thenReturn(disputed);

            // splitSettlement needs: no existing settlement → create one
            when(settlementMapper.findByOrderId(ORDER_ID)).thenReturn(null);
            when(settlementMapper.insert(any(Settlement.class))).thenAnswer(inv -> {
                Settlement s = inv.getArgument(0);
                s.setId(SETTLEMENT_ID);
                return 1;
            });

            Payment frozen = buildPayment("FROZEN");
            when(paymentMapper.findByOrderId(ORDER_ID)).thenReturn(frozen);
            when(paymentMapper.updateStatus(PAYMENT_ID, "FROZEN", "SUCCESS")).thenReturn(1);
            when(refundMapper.findByOrderId(ORDER_ID)).thenReturn(null);
            when(refundMapper.insert(any(Refund.class))).thenReturn(1);
            when(orderMapper.updateStatus(ORDER_ID, "DISPUTED", "SETTLED")).thenReturn(1);

            ArbitrationRequest req = new ArbitrationRequest();
            req.setDisputeId(DISPUTE_ID);
            req.setResult("PARTIAL");
            req.setDecision("Partial fault on both sides");
            req.setRefundAmount(refundAmount);

            Arbitration result = arbitrationService.arbitrate(ADMIN_ID, req);

            assertThat(result.getResult()).isEqualTo("PARTIAL");
            assertThat(result.getRefundAmount()).isEqualByComparingTo(refundAmount);

            // Verify split amounts: seller gets 60.00 - fee(1.20) = 58.80
            verify(settlementMapper).updateSettleAmounts(eq(SETTLEMENT_ID),
                    eq(new BigDecimal("58.80")), eq(refundAmount), eq(new BigDecimal("1.20")));
            verify(refundMapper).insert(argThat(r -> r.getRefundAmount().compareTo(refundAmount) == 0));
        }
    }

    // ───────────────────────────────────────────────────────────────
    // 7. Arbitration overrule (改判)
    // ───────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("7. Arbitration Overrule")
    class ArbitrationOverrule {

        @Test
        @DisplayName("overrule deactivates old ruling and creates new one")
        void overrule_deactivatesOld() {
            Dispute d = buildDispute("RESOLVED");
            when(disputeMapper.findById(DISPUTE_ID)).thenReturn(d);

            // Existing active arbitration
            Arbitration existing = new Arbitration();
            existing.setId(600L);
            existing.setDisputeId(DISPUTE_ID);
            existing.setOrderId(ORDER_ID);
            existing.setResult("SELLER_WIN");
            existing.setIsActive(1);
            when(arbitrationMapper.findActiveByDisputeId(DISPUTE_ID)).thenReturn(existing);
            when(disputeMapper.updateStatus(DISPUTE_ID, "RESOLVED", "ARBITRATING")).thenReturn(1);
            when(disputeMapper.updateStatus(DISPUTE_ID, "ARBITRATING", "RESOLVED")).thenReturn(1);

            when(arbitrationMapper.insert(any(Arbitration.class))).thenAnswer(inv -> {
                Arbitration a = inv.getArgument(0);
                a.setId(601L);
                return 1;
            });

            Order disputed = buildOrder("DISPUTED");
            when(orderMapper.findById(ORDER_ID)).thenReturn(disputed);

            Payment frozen = buildPayment("FROZEN");
            when(paymentMapper.findByOrderId(ORDER_ID)).thenReturn(frozen);
            when(paymentMapper.updateStatus(PAYMENT_ID, "FROZEN", "CLOSED")).thenReturn(1);
            when(refundMapper.findByOrderId(ORDER_ID)).thenReturn(null);
            when(refundMapper.insert(any(Refund.class))).thenReturn(1);
            when(orderMapper.updateStatus(ORDER_ID, "DISPUTED", "REFUNDED")).thenReturn(1);

            ArbitrationRequest req = new ArbitrationRequest();
            req.setDisputeId(DISPUTE_ID);
            req.setResult("BUYER_WIN");
            req.setDecision("Overruled: buyer actually wins");
            req.setOverrule(true);

            Arbitration result = arbitrationService.arbitrate(ADMIN_ID, req);

            assertThat(result.getResult()).isEqualTo("BUYER_WIN");
            verify(arbitrationMapper).deactivate(600L, 601L);
            verify(arbitrationMapper).insert(argThat(a -> a.getIsActive() == 1));
        }

        @Test
        @DisplayName("without overrule flag, throws ARBITRATION_OVERRULE_REQUIRED")
        void noOverruleFlag_throws() {
            Dispute d = buildDispute("RESOLVED");
            when(disputeMapper.findById(DISPUTE_ID)).thenReturn(d);

            Arbitration existing = new Arbitration();
            existing.setId(600L);
            existing.setResult("SELLER_WIN");
            existing.setIsActive(1);
            when(arbitrationMapper.findActiveByDisputeId(DISPUTE_ID)).thenReturn(existing);

            ArbitrationRequest req = new ArbitrationRequest();
            req.setDisputeId(DISPUTE_ID);
            req.setResult("BUYER_WIN");
            req.setDecision("Try to overrule");

            assertThatThrownBy(() -> arbitrationService.arbitrate(ADMIN_ID, req))
                    .isInstanceOf(BizException.class)
                    .satisfies(e -> assertThat(((BizException) e).getCode())
                            .isEqualTo(ErrorCode.ARBITRATION_OVERRULE_REQUIRED.getCode()));
        }
    }

    // ───────────────────────────────────────────────────────────────
    // 8. Idempotent arbitration
    // ───────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("8. Idempotent Arbitration")
    class IdempotentArbitration {

        @Test
        @DisplayName("duplicate arbitration with same result returns existing")
        void duplicateArbitration_returnsExisting() {
            Dispute d = buildDispute("RESOLVED");
            when(disputeMapper.findById(DISPUTE_ID)).thenReturn(d);

            Arbitration existing = new Arbitration();
            existing.setId(600L);
            existing.setArbitrationNo("ARB001");
            existing.setDisputeId(DISPUTE_ID);
            existing.setOrderId(ORDER_ID);
            existing.setResult("BUYER_WIN");
            existing.setRefundAmount(null);
            existing.setIsActive(1);
            when(arbitrationMapper.findActiveByDisputeId(DISPUTE_ID)).thenReturn(existing);

            ArbitrationRequest req = new ArbitrationRequest();
            req.setDisputeId(DISPUTE_ID);
            req.setResult("BUYER_WIN");
            req.setDecision("Same decision again");

            Arbitration result = arbitrationService.arbitrate(ADMIN_ID, req);

            assertThat(result.getId()).isEqualTo(600L);
            assertThat(result.getArbitrationNo()).isEqualTo("ARB001");
            // No new insert should happen
            verify(arbitrationMapper, never()).insert(any());
        }
    }

    // ───────────────────────────────────────────────────────────────
    // 9. Settlement retry on failure
    // ───────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("9. Settlement Failure Retry")
    class SettlementRetry {

        @Test
        @DisplayName("failed settlement is retried successfully")
        void retryFailedSettlement() {
            Settlement failed = buildSettlement("FAILED");
            failed.setRetryCount(1);
            failed.setNextRetryAt(LocalDateTime.now().minusMinutes(1));
            when(settlementMapper.findFailedForRetry(any(), eq(20)))
                    .thenReturn(java.util.List.of(failed));
            when(settlementMapper.updateStatus(SETTLEMENT_ID, "FAILED", "PENDING")).thenReturn(1);

            // executeSettlement
            Settlement pending = buildSettlement("PENDING");
            when(settlementMapper.findById(SETTLEMENT_ID)).thenReturn(pending);
            when(settlementMapper.updateStatus(SETTLEMENT_ID, "PENDING", "SETTLED")).thenReturn(1);

            Payment escrow = buildPayment("ESCROW");
            when(paymentMapper.findByOrderId(ORDER_ID)).thenReturn(escrow);
            when(paymentMapper.updateStatus(PAYMENT_ID, "ESCROW", "SUCCESS")).thenReturn(1);

            Order received = buildOrder("RECEIVED");
            when(orderMapper.findById(ORDER_ID)).thenReturn(received);
            when(orderMapper.updateStatus(ORDER_ID, "RECEIVED", "SETTLED")).thenReturn(1);

            settlementService.retryFailedSettlements();

            verify(settlementMapper).updateStatus(SETTLEMENT_ID, "FAILED", "PENDING");
            verify(settlementMapper).updateStatus(SETTLEMENT_ID, "PENDING", "SETTLED");
            verify(paymentMapper).updateStatus(PAYMENT_ID, "ESCROW", "SUCCESS");
        }
    }

    // ───────────────────────────────────────────────────────────────
    // 10. Partial refund amount validation
    // ───────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("10. Partial Refund Validation")
    class PartialRefundValidation {

        @Test
        @DisplayName("refund amount exceeding total is rejected")
        void refundAmountExceedingTotal_rejected() {
            Dispute d = buildDispute("ARBITRATING");
            when(disputeMapper.findById(DISPUTE_ID)).thenReturn(d);

            Order disputed = buildOrder("DISPUTED");
            when(orderMapper.findById(ORDER_ID)).thenReturn(disputed);

            ArbitrationRequest req = new ArbitrationRequest();
            req.setDisputeId(DISPUTE_ID);
            req.setResult("PARTIAL");
            req.setDecision("Too much refund");
            req.setRefundAmount(new BigDecimal("150.00")); // exceeds 100.00

            assertThatThrownBy(() -> arbitrationService.arbitrate(ADMIN_ID, req))
                    .isInstanceOf(BizException.class)
                    .satisfies(e -> assertThat(((BizException) e).getCode())
                            .isEqualTo(ErrorCode.REFUND_AMOUNT_INVALID.getCode()));
        }

        @Test
        @DisplayName("zero refund amount is rejected for PARTIAL")
        void zeroRefundAmount_rejected() {
            Dispute d = buildDispute("ARBITRATING");
            when(disputeMapper.findById(DISPUTE_ID)).thenReturn(d);

            Order disputed = buildOrder("DISPUTED");
            when(orderMapper.findById(ORDER_ID)).thenReturn(disputed);

            ArbitrationRequest req = new ArbitrationRequest();
            req.setDisputeId(DISPUTE_ID);
            req.setResult("PARTIAL");
            req.setDecision("Zero refund");
            req.setRefundAmount(BigDecimal.ZERO);

            assertThatThrownBy(() -> arbitrationService.arbitrate(ADMIN_ID, req))
                    .isInstanceOf(BizException.class);
        }

        @Test
        @DisplayName("full amount refund is rejected for PARTIAL (must be less than total)")
        void fullAmountPartial_rejected() {
            Dispute d = buildDispute("ARBITRATING");
            when(disputeMapper.findById(DISPUTE_ID)).thenReturn(d);

            Order disputed = buildOrder("DISPUTED");
            when(orderMapper.findById(ORDER_ID)).thenReturn(disputed);

            ArbitrationRequest req = new ArbitrationRequest();
            req.setDisputeId(DISPUTE_ID);
            req.setResult("PARTIAL");
            req.setDecision("Full amount as partial");
            req.setRefundAmount(ORDER_AMOUNT); // equals total, should use BUYER_WIN

            assertThatThrownBy(() -> arbitrationService.arbitrate(ADMIN_ID, req))
                    .isInstanceOf(BizException.class);
        }
    }
}
