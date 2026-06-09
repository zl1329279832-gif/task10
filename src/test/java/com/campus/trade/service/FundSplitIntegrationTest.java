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
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.function.Consumer;

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
    @Mock private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setupTransactionTemplate() {
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        lenient().doAnswer(invocation -> {
            Consumer<?> consumer = invocation.getArgument(0);
            consumer.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

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
        when(distributedLock.tryLock("fundsplit:1")).thenReturn(new DistributedLock.LockHandle("fundsplit:1", "token"));
        when(fundSplitMapper.findActiveByArbitrationId(1L)).thenReturn(null);
        when(paymentMapper.findById(1L)).thenReturn(pmt);
        when(tradeConfig.getPlatformFeeRate()).thenReturn(new BigDecimal("0.02"));
        when(orderMapper.findById(100L)).thenReturn(ord);
        when(refundMapper.insert(any())).thenReturn(1);
        when(settlementMapper.insert(any())).thenReturn(1);
        when(fundSplitMapper.insert(any())).thenAnswer(inv -> { FundSplit fs = inv.getArgument(0); fs.setId(1L); return 1; });
        when(paymentMapper.unfreeze(1L, "SUCCESS")).thenReturn(1);
        when(fundSplitMapper.updateStatus(1L, "PENDING", "EXECUTED")).thenReturn(1);
        when(fundSplitMapper.sumActiveSplitAmountsByOrderId(100L)).thenReturn(new BigDecimal("200.00"));
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
        when(distributedLock.tryLock("fundsplit:1")).thenReturn(new DistributedLock.LockHandle("fundsplit:1", "token"));
        when(fundSplitMapper.findActiveByArbitrationId(1L)).thenReturn(null);
        when(paymentMapper.findById(1L)).thenReturn(pmt);
        when(tradeConfig.getPlatformFeeRate()).thenReturn(new BigDecimal("0.02"));
        when(orderMapper.findById(100L)).thenReturn(ord);
        when(refundMapper.insert(any())).thenReturn(1);
        when(fundSplitMapper.insert(any())).thenAnswer(inv -> { FundSplit fs = inv.getArgument(0); fs.setId(1L); return 1; });
        when(paymentMapper.unfreeze(1L, "CLOSED")).thenReturn(1);
        when(fundSplitMapper.updateStatus(1L, "PENDING", "EXECUTED")).thenReturn(1);
        when(fundSplitMapper.sumActiveSplitAmountsByOrderId(100L)).thenReturn(new BigDecimal("200.00"));
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
        when(distributedLock.tryLock("fundsplit:1")).thenReturn(new DistributedLock.LockHandle("fundsplit:1", "token"));
        when(fundSplitMapper.findActiveByArbitrationId(1L)).thenReturn(null);
        when(paymentMapper.findById(1L)).thenReturn(pmt);
        when(tradeConfig.getPlatformFeeRate()).thenReturn(new BigDecimal("0.02"));
        when(orderMapper.findById(100L)).thenReturn(ord);
        when(settlementMapper.insert(any())).thenReturn(1);
        when(fundSplitMapper.insert(any())).thenAnswer(inv -> { FundSplit fs = inv.getArgument(0); fs.setId(1L); return 1; });
        when(paymentMapper.unfreeze(1L, "SUCCESS")).thenReturn(1);
        when(fundSplitMapper.updateStatus(1L, "PENDING", "EXECUTED")).thenReturn(1);
        when(fundSplitMapper.sumActiveSplitAmountsByOrderId(100L)).thenReturn(new BigDecimal("200.00"));
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
        when(distributedLock.tryLock("fundsplit:1")).thenReturn(new DistributedLock.LockHandle("fundsplit:1", "token"));
        when(fundSplitMapper.findActiveByArbitrationId(1L)).thenReturn(existing);

        FundSplit result = fundSplitService.executeFundSplit(1L, 100L, 1L,
                new BigDecimal("80.00"), new BigDecimal("120.00"), 99L);

        assertThat(result).isSameAs(existing);
        verify(fundSplitMapper, never()).insert(any());
    }

    @Test @DisplayName("Cancel active split for arbitration reversal") void cancelActiveSplit() {
        FundSplit active = new FundSplit();
        active.setId(1L); active.setStatus(FundSplitStatus.EXECUTED.name());
        when(distributedLock.tryLock("fundsplit:1")).thenReturn(new DistributedLock.LockHandle("fundsplit:1", "token"));
        when(fundSplitMapper.findActiveByArbitrationId(1L)).thenReturn(active);
        when(fundSplitMapper.updateStatus(1L, "EXECUTED", "CANCELLED")).thenReturn(1);

        fundSplitService.cancelActiveSplit(1L, 99L);

        verify(fundSplitMapper).updateStatus(1L, "EXECUTED", "CANCELLED");
        verify(auditService).log(eq(99L), isNull(), eq("FUND_SPLIT"), eq("CANCEL"), eq("FUND_SPLIT"), eq(1L), anyString());
    }

    @Test @DisplayName("Cancel no-op when no active split") void cancelNoOp() {
        when(distributedLock.tryLock("fundsplit:1")).thenReturn(new DistributedLock.LockHandle("fundsplit:1", "token"));
        when(fundSplitMapper.findActiveByArbitrationId(1L)).thenReturn(null);

        fundSplitService.cancelActiveSplit(1L, 99L);

        verify(fundSplitMapper, never()).updateStatus(anyLong(), anyString(), anyString());
    }
}
