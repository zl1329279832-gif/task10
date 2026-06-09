package com.campus.trade.service;

import com.campus.trade.common.config.TradeConfig;
import com.campus.trade.common.exception.BizException;
import com.campus.trade.common.util.DistributedLock;
import com.campus.trade.domain.entity.FundSplit;
import com.campus.trade.domain.entity.Order;
import com.campus.trade.domain.entity.Payment;
import com.campus.trade.domain.entity.Settlement;
import com.campus.trade.domain.enums.FundSplitStatus;
import com.campus.trade.domain.enums.PaymentStatus;
import com.campus.trade.mapper.*;
import com.campus.trade.service.impl.FundSplitServiceImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FundSplitService Integration")
class FundSplitIntegrationTest {

    @InjectMocks private FundSplitServiceImpl fundSplitService;

    @Mock private FundSplitMapper fundSplitMapper;
    @Mock private PaymentMapper paymentMapper;
    @Mock private RefundMapper refundMapper;
    @Mock private SettlementMapper settlementMapper;
    @Mock private OrderMapper orderMapper;
    @Mock private AuditService auditService;
    @Mock private DistributedLock distributedLock;
    @Mock private TradeConfig tradeConfig;

    private Payment buildPayment(Long id, Long orderId, BigDecimal amount) {
        Payment p = new Payment();
        p.setId(id); p.setOrderId(orderId); p.setAmount(amount);
        p.setStatus(PaymentStatus.FROZEN.name());
        return p;
    }

    private Order buildOrder(Long id, String orderNo, Long buyerId, Long sellerId) {
        Order o = new Order();
        o.setId(id); o.setOrderNo(orderNo); o.setBuyerId(buyerId); o.setSellerId(sellerId);
        o.setTotalAmount(new BigDecimal("200.00"));
        return o;
    }

    @Test @DisplayName("Execute partial split: buyer refund + seller settle") void partialSplit() {
        Payment pmt = buildPayment(1L, 100L, new BigDecimal("200.00"));
        Order ord = buildOrder(100L, "ORD001", 10L, 20L);
        when(distributedLock.tryLock("fundsplit:1")).thenReturn(true);
        when(fundSplitMapper.findActiveByArbitrationId(1L)).thenReturn(null);
        when(paymentMapper.findById(1L)).thenReturn(pmt);
        when(tradeConfig.getPlatformFeeRate()).thenReturn(new BigDecimal("0.02"));
        when(orderMapper.findById(100L)).thenReturn(ord);
        when(refundMapper.insert(any())).thenReturn(1);
        when(settlementMapper.insert(any())).thenReturn(1);
        when(fundSplitMapper.insert(any())).thenAnswer(inv -> { FundSplit fs = inv.getArgument(0); fs.setId(1L); return 1; });
        when(paymentMapper.unfreeze(1L, "SUCCESS")).thenReturn(1);
        when(fundSplitMapper.updateStatus(1L, "PENDING", "EXECUTED")).thenReturn(1);
        FundSplit executed = new FundSplit(); executed.setId(1L); executed.setStatus("EXECUTED");
        when(fundSplitMapper.findById(1L)).thenReturn(executed);

        FundSplit result = fundSplitService.executeFundSplit(1L, 100L, 1L,
                new BigDecimal("80.00"), new BigDecimal("120.00"), 99L);

        assertThat(result.getStatus()).isEqualTo("EXECUTED");
        verify(refundMapper).insert(argThat(r -> r.getRefundAmount().compareTo(new BigDecimal("80.00")) == 0));
        verify(settlementMapper).insert(argThat(s -> s.getOrderAmount().compareTo(new BigDecimal("120.00")) == 0));
        verify(paymentMapper).unfreeze(1L, "SUCCESS"); // partial -> SUCCESS not CLOSED
    }

    @Test @DisplayName("Execute BUYER_WIN: full refund, payment CLOSED") void buyerWinFullRefund() {
        Payment pmt = buildPayment(1L, 100L, new BigDecimal("200.00"));
        Order ord = buildOrder(100L, "ORD001", 10L, 20L);
        when(distributedLock.tryLock("fundsplit:1")).thenReturn(true);
        when(fundSplitMapper.findActiveByArbitrationId(1L)).thenReturn(null);
        when(paymentMapper.findById(1L)).thenReturn(pmt);
        when(tradeConfig.getPlatformFeeRate()).thenReturn(new BigDecimal("0.02"));
        when(orderMapper.findById(100L)).thenReturn(ord);
        when(refundMapper.insert(any())).thenReturn(1);
        when(fundSplitMapper.insert(any())).thenAnswer(inv -> { FundSplit fs = inv.getArgument(0); fs.setId(1L); return 1; });
        when(paymentMapper.unfreeze(1L, "CLOSED")).thenReturn(1);
        when(fundSplitMapper.updateStatus(1L, "PENDING", "EXECUTED")).thenReturn(1);
        FundSplit executed = new FundSplit(); executed.setId(1L); executed.setStatus("EXECUTED");
        when(fundSplitMapper.findById(1L)).thenReturn(executed);

        FundSplit result = fundSplitService.executeFundSplit(1L, 100L, 1L,
                new BigDecimal("200.00"), BigDecimal.ZERO, 99L);

        assertThat(result.getStatus()).isEqualTo("EXECUTED");
        verify(paymentMapper).unfreeze(1L, "CLOSED"); // full refund -> CLOSED
        verify(settlementMapper, never()).insert(any()); // no seller settlement
    }

    @Test @DisplayName("Execute SELLER_WIN: full settle, no refund") void sellerWinFullSettle() {
        Payment pmt = buildPayment(1L, 100L, new BigDecimal("200.00"));
        Order ord = buildOrder(100L, "ORD001", 10L, 20L);
        when(distributedLock.tryLock("fundsplit:1")).thenReturn(true);
        when(fundSplitMapper.findActiveByArbitrationId(1L)).thenReturn(null);
        when(paymentMapper.findById(1L)).thenReturn(pmt);
        when(tradeConfig.getPlatformFeeRate()).thenReturn(new BigDecimal("0.02"));
        when(orderMapper.findById(100L)).thenReturn(ord);
        when(settlementMapper.insert(any())).thenReturn(1);
        when(fundSplitMapper.insert(any())).thenAnswer(inv -> { FundSplit fs = inv.getArgument(0); fs.setId(1L); return 1; });
        when(paymentMapper.unfreeze(1L, "SUCCESS")).thenReturn(1);
        when(fundSplitMapper.updateStatus(1L, "PENDING", "EXECUTED")).thenReturn(1);
        FundSplit executed = new FundSplit(); executed.setId(1L); executed.setStatus("EXECUTED");
        when(fundSplitMapper.findById(1L)).thenReturn(executed);

        FundSplit result = fundSplitService.executeFundSplit(1L, 100L, 1L,
                BigDecimal.ZERO, new BigDecimal("200.00"), 99L);

        assertThat(result.getStatus()).isEqualTo("EXECUTED");
        verify(refundMapper, never()).insert(any()); // no buyer refund
        verify(settlementMapper).insert(argThat(s -> s.getOrderAmount().compareTo(new BigDecimal("200.00")) == 0));
    }

    @Test @DisplayName("Idempotent: repeat call returns existing EXECUTED split") void idempotent() {
        FundSplit existing = new FundSplit();
        existing.setId(1L); existing.setStatus(FundSplitStatus.EXECUTED.name());
        when(distributedLock.tryLock("fundsplit:1")).thenReturn(true);
        when(fundSplitMapper.findActiveByArbitrationId(1L)).thenReturn(existing);

        FundSplit result = fundSplitService.executeFundSplit(1L, 100L, 1L,
                new BigDecimal("80.00"), new BigDecimal("120.00"), 99L);

        assertThat(result).isSameAs(existing);
        verify(fundSplitMapper, never()).insert(any());
    }

    @Test @DisplayName("Cancel active split reverses refund, settlement, and re-freezes payment") void cancelActiveSplit() {
        FundSplit active = new FundSplit();
        active.setId(1L); active.setStatus(FundSplitStatus.EXECUTED.name());
        active.setPaymentId(1L); active.setBuyerRefundNo("REF001"); active.setSellerSettlementNo("STL001");

        com.campus.trade.domain.entity.Refund refund = new com.campus.trade.domain.entity.Refund();
        refund.setId(10L); refund.setRefundNo("REF001"); refund.setRefundAmount(new BigDecimal("80.00"));
        refund.setStatus("SUCCESS");

        Settlement settlement = new Settlement();
        settlement.setId(20L); settlement.setSettlementNo("STL001"); settlement.setSettleAmount(new BigDecimal("117.60"));
        settlement.setStatus("SETTLED");

        Payment pmt = buildPayment(1L, 100L, new BigDecimal("200.00"));
        pmt.setStatus("SUCCESS"); // payment was unfrozen by original split

        when(fundSplitMapper.findActiveByArbitrationId(1L)).thenReturn(active);
        when(fundSplitMapper.updateStatus(1L, "EXECUTED", "CANCELLED")).thenReturn(1);
        when(refundMapper.findByRefundNo("REF001")).thenReturn(refund);
        when(refundMapper.updateStatus(10L, "SUCCESS", "REVERSED")).thenReturn(1);
        when(settlementMapper.findBySettlementNo("STL001")).thenReturn(settlement);
        when(settlementMapper.updateStatus(20L, "SETTLED", "REVERSED")).thenReturn(1);
        when(paymentMapper.findById(1L)).thenReturn(pmt);
        when(paymentMapper.freeze(eq(1L), eq("SUCCESS"), eq(new BigDecimal("200.00")), eq("Arbitration reversal"))).thenReturn(1);

        fundSplitService.cancelActiveSplit(1L, 100L, 99L);

        verify(fundSplitMapper).updateStatus(1L, "EXECUTED", "CANCELLED");
        verify(refundMapper).updateStatus(10L, "SUCCESS", "REVERSED");
        verify(settlementMapper).updateStatus(20L, "SETTLED", "REVERSED");
        verify(paymentMapper).freeze(1L, "SUCCESS", new BigDecimal("200.00"), "Arbitration reversal");
        verify(auditService).logSync(eq(99L), isNull(), eq("FUND_SPLIT"), eq("CANCEL"), eq("FUND_SPLIT"), eq(1L), anyString());
    }

    @Test @DisplayName("Cancel no-op when no active split") void cancelNoOp() {
        when(fundSplitMapper.findActiveByArbitrationId(1L)).thenReturn(null);

        fundSplitService.cancelActiveSplit(1L, 100L, 99L);

        verify(fundSplitMapper, never()).updateStatus(anyLong(), anyString(), anyString());
    }
}
