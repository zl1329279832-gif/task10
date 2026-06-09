package com.campus.trade.service;

import com.campus.trade.common.config.TradeConfig;
import com.campus.trade.common.exception.BizException;
import com.campus.trade.common.util.DistributedLock;
import com.campus.trade.domain.entity.Arbitration;
import com.campus.trade.domain.entity.Dispute;
import com.campus.trade.domain.entity.FundSplit;
import com.campus.trade.domain.entity.Payment;
import com.campus.trade.domain.enums.*;
import com.campus.trade.dto.request.ArbitrationRequest;
import com.campus.trade.mapper.*;
import com.campus.trade.service.impl.ArbitrationServiceImpl;
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
 * Full lifecycle tests: order -> pay -> dispute -> freeze -> arbitrate -> split/refund
 * Plus reversal and idempotency tests.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Arbitration Freeze Flow - Full Lifecycle")
class ArbitrationFreezeFlowTest {

    @InjectMocks private ArbitrationServiceImpl arbitrationService;

    @Mock private ArbitrationMapper arbitrationMapper;
    @Mock private DisputeMapper disputeMapper;
    @Mock private OrderMapper orderMapper;
    @Mock private PaymentMapper paymentMapper;
    @Mock private OrderService orderService;
    @Mock private DisputeService disputeService;
    @Mock private FundSplitService fundSplitService;
    @Mock private EscrowService escrowService;
    @Mock private AuditService auditService;
    @Mock private DistributedLock distributedLock;
    @Mock private TradeConfig tradeConfig;

    private com.campus.trade.domain.entity.Order order;
    private Payment payment;
    private Dispute dispute;

    @BeforeEach
    void setupEntities() {
        order = new com.campus.trade.domain.entity.Order();
        order.setId(100L); order.setOrderNo("ORD001"); order.setBuyerId(10L); order.setSellerId(20L);
        order.setTotalAmount(new BigDecimal("200.00")); order.setStatus(OrderStatus.DISPUTED.name());
        order.setPayTime(LocalDateTime.now());

        payment = new Payment();
        payment.setId(1L); payment.setOrderId(100L); payment.setAmount(new BigDecimal("200.00"));
        payment.setStatus(PaymentStatus.FROZEN.name());

        dispute = new Dispute();
        dispute.setId(1L); dispute.setDisputeNo("DSP001"); dispute.setOrderId(100L);
        dispute.setOrderNo("ORD001"); dispute.setInitiatorId(10L); dispute.setRespondentId(20L);
        dispute.setStatus(DisputeStatus.ARBITRATING.name());
    }

    private ArbitrationRequest buildReq(String result, BigDecimal refundAmount, BigDecimal sellerAmount) {
        ArbitrationRequest r = new ArbitrationRequest();
        r.setDisputeId(1L); r.setResult(result); r.setDecision("Test decision");
        r.setRefundAmount(refundAmount); r.setSellerAmount(sellerAmount);
        return r;
    }

    private void mockCommonSetup() {
        when(disputeMapper.findById(1L)).thenReturn(dispute);
        when(arbitrationMapper.findByDisputeId(1L)).thenReturn(null); // first call: no existing
        when(orderMapper.findById(100L)).thenReturn(order);
        when(paymentMapper.findByOrderId(100L)).thenReturn(payment);
        when(distributedLock.tryLock("arbitrate:1")).thenReturn(true);
        when(arbitrationMapper.insert(any())).thenAnswer(inv -> {
            Arbitration a = inv.getArgument(0);
            a.setId(1L);
            return 1;
        });
        when(disputeMapper.updateStatus(1L, "ARBITRATING", "RESOLVED")).thenReturn(1);
    }

    // ═══════════════════════════════════════════
    //  Full lifecycle tests
    // ═══════════════════════════════════════════

    @Test @DisplayName("BUYER_WIN: full refund -> order REFUNDED") void fullFlow_buyerWin() {
        mockCommonSetup();
        when(fundSplitService.executeFundSplit(anyLong(), eq(100L), eq(1L),
                eq(new BigDecimal("200.00")), eq(BigDecimal.ZERO), eq(99L)))
                .thenReturn(new FundSplit());

        ArbitrationRequest req = buildReq("BUYER_WIN", new BigDecimal("200.00"), null);
        Arbitration result = arbitrationService.arbitrate(99L, req);

        assertThat(result.getResult()).isEqualTo("BUYER_WIN");
        assertThat(result.getRefundAmount()).isEqualByComparingTo("200.00");
        assertThat(result.getSellerAmount()).isEqualByComparingTo("0.00");
        verify(fundSplitService).executeFundSplit(anyLong(), eq(100L), eq(1L),
                eq(new BigDecimal("200.00")), eq(BigDecimal.ZERO), eq(99L));
        verify(orderService).transitionOrder(100L, OrderStatus.REFUNDED.name(), 99L, "Arbitration: buyer wins");
        verify(disputeMapper).updateStatus(1L, "ARBITRATING", "RESOLVED");
    }

    @Test @DisplayName("SELLER_WIN: fund split handles unfreeze internally -> order SHIPPED/PAID") void fullFlow_sellerWin() {
        mockCommonSetup();
        when(fundSplitService.executeFundSplit(anyLong(), eq(100L), eq(1L),
                eq(BigDecimal.ZERO), eq(new BigDecimal("200.00")), eq(99L)))
                .thenReturn(new FundSplit());

        ArbitrationRequest req = buildReq("SELLER_WIN", null, null);
        Arbitration result = arbitrationService.arbitrate(99L, req);

        assertThat(result.getResult()).isEqualTo("SELLER_WIN");
        assertThat(result.getRefundAmount()).isEqualByComparingTo("0.00");
        assertThat(result.getSellerAmount()).isEqualByComparingTo("200.00");
        // No separate unfreeze call — executeFundSplit handles it internally
        verify(escrowService, never()).unfreezeFunds(anyLong(), anyLong(), anyString());
        verify(fundSplitService).executeFundSplit(anyLong(), eq(100L), eq(1L),
                eq(BigDecimal.ZERO), eq(new BigDecimal("200.00")), eq(99L));
        verify(orderService).transitionOrder(eq(100L), eq(OrderStatus.PAID.name()), eq(99L), eq("Arbitration: seller wins"));
    }

    @Test @DisplayName("PARTIAL: fund split with buyer refund + seller settle") void fullFlow_partial() {
        mockCommonSetup();
        when(fundSplitService.executeFundSplit(anyLong(), eq(100L), eq(1L),
                eq(new BigDecimal("80.00")), eq(new BigDecimal("120.00")), eq(99L)))
                .thenReturn(new FundSplit());

        ArbitrationRequest req = buildReq("PARTIAL", new BigDecimal("80.00"), new BigDecimal("120.00"));
        Arbitration result = arbitrationService.arbitrate(99L, req);

        assertThat(result.getResult()).isEqualTo("PARTIAL");
        assertThat(result.getRefundAmount()).isEqualByComparingTo("80.00");
        assertThat(result.getSellerAmount()).isEqualByComparingTo("120.00");
        verify(fundSplitService).executeFundSplit(anyLong(), eq(100L), eq(1L),
                eq(new BigDecimal("80.00")), eq(new BigDecimal("120.00")), eq(99L));
    }

    // ═══════════════════════════════════════════
    //  Idempotency
    // ═══════════════════════════════════════════

    @Test @DisplayName("Idempotent: repeat arbitration returns existing record") void idempotentArbitration() {
        Arbitration existing = new Arbitration();
        existing.setId(1L); existing.setResult("BUYER_WIN");
        existing.setRefundAmount(new BigDecimal("200.00"));

        when(disputeMapper.findById(1L)).thenReturn(dispute);
        when(arbitrationMapper.findByDisputeId(1L)).thenReturn(existing);

        ArbitrationRequest req = buildReq("BUYER_WIN", new BigDecimal("200.00"), null);
        Arbitration result = arbitrationService.arbitrate(99L, req);

        assertThat(result).isSameAs(existing);
        verify(arbitrationMapper, never()).insert(any());
        verify(fundSplitService, never()).executeFundSplit(anyLong(), anyLong(), anyLong(), any(), any(), anyLong());
    }

    // ═══════════════════════════════════════════
    //  Arbitration reversal
    // ═══════════════════════════════════════════

    @Test @DisplayName("Reversal: cancel old split, update ruling, execute new split") void arbitrationReversal() {
        Arbitration oldArb = new Arbitration();
        oldArb.setId(1L); oldArb.setResult("BUYER_WIN");
        oldArb.setRefundAmount(new BigDecimal("200.00"));

        when(disputeMapper.findById(1L)).thenReturn(dispute);
        dispute.setStatus(DisputeStatus.RESOLVED.name());
        when(arbitrationMapper.findByDisputeId(1L)).thenReturn(oldArb);
        when(orderMapper.findById(100L)).thenReturn(order);
        when(paymentMapper.findByOrderId(100L)).thenReturn(payment);
        when(distributedLock.tryLock("arbitrate:1")).thenReturn(true);
        when(arbitrationMapper.updateRuling(eq(1L), anyString(), anyString(), any(), any())).thenReturn(1);
        when(disputeMapper.updateStatus(1L, "RESOLVED", "ARBITRATING")).thenReturn(1);
        when(disputeMapper.updateStatus(1L, "ARBITRATING", "RESOLVED")).thenReturn(1);
        when(fundSplitService.executeFundSplit(anyLong(), anyLong(), anyLong(), any(), any(), anyLong()))
                .thenReturn(new FundSplit());
        Arbitration updated = new Arbitration();
        updated.setId(1L); updated.setResult("PARTIAL");
        updated.setRefundAmount(new BigDecimal("80.00")); updated.setSellerAmount(new BigDecimal("120.00"));
        when(arbitrationMapper.findById(1L)).thenReturn(updated);

        ArbitrationRequest req = buildReq("PARTIAL", new BigDecimal("80.00"), new BigDecimal("120.00"));
        Arbitration result = arbitrationService.reverseArbitration(99L, req);

        verify(fundSplitService).cancelActiveSplit(1L, 100L, 99L);
        verify(arbitrationMapper).updateRuling(1L, "PARTIAL", "Test decision",
                new BigDecimal("80.00"), new BigDecimal("120.00"));
        verify(fundSplitService).executeFundSplit(eq(1L), eq(100L), eq(1L),
                eq(new BigDecimal("80.00")), eq(new BigDecimal("120.00")), eq(99L));
        assertThat(result.getResult()).isEqualTo("PARTIAL");
    }

    @Test @DisplayName("Reversal fails when no existing arbitration") void reversalFailsNoArb() {
        when(disputeMapper.findById(1L)).thenReturn(dispute);
        when(arbitrationMapper.findByDisputeId(1L)).thenReturn(null);

        ArbitrationRequest req = buildReq("SELLER_WIN", null, null);
        assertThatThrownBy(() -> arbitrationService.reverseArbitration(99L, req))
                .isInstanceOf(BizException.class);
    }

    // ═══════════════════════════════════════════
    //  PARTIAL validation
    // ═══════════════════════════════════════════

    @Test @DisplayName("PARTIAL without sellerAmount throws") void partialWithoutSellerAmount() {
        when(disputeMapper.findById(1L)).thenReturn(dispute);
        when(arbitrationMapper.findByDisputeId(1L)).thenReturn(null);
        when(orderMapper.findById(100L)).thenReturn(order);
        when(paymentMapper.findByOrderId(100L)).thenReturn(payment);

        ArbitrationRequest req = buildReq("PARTIAL", new BigDecimal("80.00"), null);
        assertThatThrownBy(() -> arbitrationService.arbitrate(99L, req))
                .isInstanceOf(BizException.class);
    }

    @Test @DisplayName("PARTIAL with wrong amounts throws") void partialWrongAmounts() {
        when(disputeMapper.findById(1L)).thenReturn(dispute);
        when(arbitrationMapper.findByDisputeId(1L)).thenReturn(null);
        when(orderMapper.findById(100L)).thenReturn(order);
        when(paymentMapper.findByOrderId(100L)).thenReturn(payment);

        // refund 80 + seller 100 = 180 != 200
        ArbitrationRequest req = buildReq("PARTIAL", new BigDecimal("80.00"), new BigDecimal("100.00"));
        assertThatThrownBy(() -> arbitrationService.arbitrate(99L, req))
                .isInstanceOf(BizException.class);
    }
}
