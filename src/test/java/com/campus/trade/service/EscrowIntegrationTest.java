package com.campus.trade.service;

import com.campus.trade.common.config.TradeConfig;
import com.campus.trade.common.exception.BizException;
import com.campus.trade.common.exception.ErrorCode;
import com.campus.trade.common.util.DistributedLock;
import com.campus.trade.domain.entity.Order;
import com.campus.trade.domain.entity.Payment;
import com.campus.trade.domain.entity.Settlement;
import com.campus.trade.domain.enums.PaymentStatus;
import com.campus.trade.domain.enums.SettlementStatus;
import com.campus.trade.dto.response.SettlementResponse;
import com.campus.trade.mapper.OrderMapper;
import com.campus.trade.mapper.PaymentMapper;
import com.campus.trade.mapper.SettlementMapper;
import com.campus.trade.service.impl.EscrowServiceImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EscrowService Integration")
class EscrowIntegrationTest {

    @InjectMocks private EscrowServiceImpl escrowService;

    @Mock private PaymentMapper paymentMapper;
    @Mock private SettlementMapper settlementMapper;
    @Mock private OrderMapper orderMapper;
    @Mock private OrderService orderService;
    @Mock private SettlementService settlementService;
    @Mock private AuditService auditService;
    @Mock private DistributedLock distributedLock;
    @Mock private TradeConfig tradeConfig;

    private Payment buildPayment(Long id, Long orderId, BigDecimal amount, String status) {
        Payment p = new Payment();
        p.setId(id); p.setOrderId(orderId); p.setAmount(amount); p.setStatus(status);
        return p;
    }

    private Settlement buildSettlement(Long id, Long orderId, BigDecimal amount, String status) {
        Settlement s = new Settlement();
        s.setId(id); s.setOrderId(orderId); s.setSettleAmount(amount); s.setStatus(status);
        return s;
    }

    // ═══════════════════════════════════════════
    //  Freeze
    // ═══════════════════════════════════════════
    @Nested @DisplayName("Freeze funds") class Freeze {
        @Test @DisplayName("Freeze SUCCESS payment and PENDING settlement") void freezeBoth() {
            Payment pmt = buildPayment(1L, 100L, new BigDecimal("200.00"), PaymentStatus.SUCCESS.name());
            Settlement stl = buildSettlement(1L, 100L, new BigDecimal("196.00"), SettlementStatus.PENDING.name());
            when(distributedLock.tryLock("order:100")).thenReturn(true);
            when(paymentMapper.findByOrderId(100L)).thenReturn(pmt);
            when(paymentMapper.freeze(1L, "SUCCESS", new BigDecimal("200.00"), "dispute")).thenReturn(1);
            when(settlementMapper.findByOrderId(100L)).thenReturn(stl);
            when(settlementMapper.freeze(1L, "PENDING", new BigDecimal("196.00"), "dispute")).thenReturn(1);

            escrowService.freezeFunds(100L, 1L, "dispute");

            verify(paymentMapper).freeze(1L, "SUCCESS", new BigDecimal("200.00"), "dispute");
            verify(settlementMapper).freeze(1L, "PENDING", new BigDecimal("196.00"), "dispute");
            verify(auditService).logSync(eq(1L), isNull(), eq("ESCROW"), eq("FREEZE_PAYMENT"), eq("PAYMENT"), eq(1L), anyString());
            verify(auditService).logSync(eq(1L), isNull(), eq("ESCROW"), eq("FREEZE_SETTLEMENT"), eq("SETTLEMENT"), eq(1L), anyString());
        }

        @Test @DisplayName("Freeze payment only (no settlement)") void freezePaymentOnly() {
            Payment pmt = buildPayment(1L, 100L, new BigDecimal("200.00"), PaymentStatus.SUCCESS.name());
            when(distributedLock.tryLock("order:100")).thenReturn(true);
            when(paymentMapper.findByOrderId(100L)).thenReturn(pmt);
            when(paymentMapper.freeze(1L, "SUCCESS", new BigDecimal("200.00"), "refund")).thenReturn(1);
            when(settlementMapper.findByOrderId(100L)).thenReturn(null);

            escrowService.freezeFunds(100L, 1L, "refund");

            verify(paymentMapper).freeze(1L, "SUCCESS", new BigDecimal("200.00"), "refund");
            verify(settlementMapper, never()).freeze(anyLong(), anyString(), any(), anyString());
        }

        @Test @DisplayName("Skip freeze when payment not SUCCESS") void skipWhenNotSuccess() {
            Payment pmt = buildPayment(1L, 100L, new BigDecimal("200.00"), PaymentStatus.PENDING.name());
            when(distributedLock.tryLock("order:100")).thenReturn(true);
            when(paymentMapper.findByOrderId(100L)).thenReturn(pmt);
            when(settlementMapper.findByOrderId(100L)).thenReturn(null);

            escrowService.freezeFunds(100L, 1L, "test");

            verify(paymentMapper, never()).freeze(anyLong(), anyString(), any(), anyString());
        }

        @Test @DisplayName("Throw when lock fails") void lockFail() {
            when(distributedLock.tryLock("order:100")).thenReturn(false);
            assertThatThrownBy(() -> escrowService.freezeFunds(100L, 1L, "test"))
                    .isInstanceOf(BizException.class);
        }
    }

    // ═══════════════════════════════════════════
    //  Unfreeze
    // ═══════════════════════════════════════════
    @Nested @DisplayName("Unfreeze funds") class Unfreeze {
        @Test @DisplayName("Unfreeze FROZEN payment and settlement") void unfreezeBoth() {
            Payment pmt = buildPayment(1L, 100L, new BigDecimal("200.00"), PaymentStatus.FROZEN.name());
            Settlement stl = buildSettlement(1L, 100L, new BigDecimal("196.00"), SettlementStatus.FROZEN.name());
            when(distributedLock.tryLock("order:100")).thenReturn(true);
            when(paymentMapper.findByOrderId(100L)).thenReturn(pmt);
            when(paymentMapper.unfreeze(1L, "SUCCESS")).thenReturn(1);
            when(settlementMapper.findByOrderId(100L)).thenReturn(stl);
            when(settlementMapper.unfreeze(1L, "PENDING")).thenReturn(1);

            escrowService.unfreezeFunds(100L, 1L, "seller wins");

            verify(paymentMapper).unfreeze(1L, "SUCCESS");
            verify(settlementMapper).unfreeze(1L, "PENDING");
        }

        @Test @DisplayName("Skip unfreeze when not frozen") void skipWhenNotFrozen() {
            Payment pmt = buildPayment(1L, 100L, new BigDecimal("200.00"), PaymentStatus.SUCCESS.name());
            when(distributedLock.tryLock("order:100")).thenReturn(true);
            when(paymentMapper.findByOrderId(100L)).thenReturn(pmt);
            when(settlementMapper.findByOrderId(100L)).thenReturn(null);

            escrowService.unfreezeFunds(100L, 1L, "test");

            verify(paymentMapper, never()).unfreeze(anyLong(), anyString());
        }
    }

    // ═══════════════════════════════════════════
    //  Auto-settle
    // ═══════════════════════════════════════════
    @Nested @DisplayName("Auto-settle on receipt") class AutoSettle {
        @Test @DisplayName("Auto-settle SUCCESS payment") void autoSettleSuccess() {
            Payment pmt = buildPayment(1L, 100L, new BigDecimal("200.00"), PaymentStatus.SUCCESS.name());
            when(distributedLock.tryLock("order:100")).thenReturn(true);
            when(paymentMapper.findByOrderId(100L)).thenReturn(pmt);
            when(settlementMapper.findByOrderId(100L)).thenReturn(null);
            SettlementResponse resp = new SettlementResponse();
            resp.setId(1L);
            when(settlementService.createSettlement(100L)).thenReturn(resp);

            escrowService.autoSettleOnReceipt(100L, 2L);

            verify(settlementService).createSettlement(100L);
            verify(settlementService).executeSettlement(1L);
        }

        @Test @DisplayName("Skip auto-settle when payment frozen") void skipWhenFrozen() {
            Payment pmt = buildPayment(1L, 100L, new BigDecimal("200.00"), PaymentStatus.FROZEN.name());
            when(distributedLock.tryLock("order:100")).thenReturn(true);
            when(paymentMapper.findByOrderId(100L)).thenReturn(pmt);

            escrowService.autoSettleOnReceipt(100L, 2L);

            verify(settlementService, never()).createSettlement(anyLong());
        }

        @Test @DisplayName("Skip auto-settle when settlement exists") void skipWhenExists() {
            Payment pmt = buildPayment(1L, 100L, new BigDecimal("200.00"), PaymentStatus.SUCCESS.name());
            Settlement stl = buildSettlement(1L, 100L, new BigDecimal("196.00"), SettlementStatus.SETTLED.name());
            when(distributedLock.tryLock("order:100")).thenReturn(true);
            when(paymentMapper.findByOrderId(100L)).thenReturn(pmt);
            when(settlementMapper.findByOrderId(100L)).thenReturn(stl);

            escrowService.autoSettleOnReceipt(100L, 2L);

            verify(settlementService, never()).createSettlement(anyLong());
        }

        @Test @DisplayName("Non-fatal when auto-settle throws") void nonFatal() {
            Payment pmt = buildPayment(1L, 100L, new BigDecimal("200.00"), PaymentStatus.SUCCESS.name());
            when(distributedLock.tryLock("order:100")).thenReturn(true);
            when(paymentMapper.findByOrderId(100L)).thenReturn(pmt);
            when(settlementMapper.findByOrderId(100L)).thenReturn(null);
            when(settlementService.createSettlement(100L)).thenThrow(new BizException(ErrorCode.SETTLEMENT_ALREADY_EXISTS));

            // Should not throw
            assertThatCode(() -> escrowService.autoSettleOnReceipt(100L, 2L)).doesNotThrowAnyException();
        }
    }

    // ═══════════════════════════════════════════
    //  Retry settlement
    // ═══════════════════════════════════════════
    @Nested @DisplayName("Retry settlement") class Retry {
        @Test @DisplayName("Retry FAILED settlement delegates to settlementService") void retrySuccess() {
            Settlement stl = buildSettlement(1L, 100L, new BigDecimal("196.00"), SettlementStatus.FAILED.name());
            stl.setRetryCount(0); stl.setOrderId(100L);
            when(settlementMapper.findById(1L)).thenReturn(stl);
            when(paymentMapper.findByOrderId(100L)).thenReturn(
                    buildPayment(1L, 100L, new BigDecimal("200.00"), PaymentStatus.SUCCESS.name()));
            SettlementResponse resp = new SettlementResponse();
            resp.setId(1L); resp.setStatus("SETTLED");
            when(settlementService.retrySettlement(1L)).thenReturn(resp);

            SettlementResponse result = escrowService.retrySettlement(1L, 1L);

            verify(settlementService).retrySettlement(1L);
            verify(auditService).logSync(eq(1L), isNull(), eq("ESCROW"), eq("RETRY_SETTLEMENT"),
                    eq("SETTLEMENT"), eq(1L), anyString());
            assertThat(result.getStatus()).isEqualTo("SETTLED");
        }

        @Test @DisplayName("Reject retry when payment frozen") void rejectWhenPaymentFrozen() {
            Settlement stl = buildSettlement(1L, 100L, new BigDecimal("196.00"), SettlementStatus.FAILED.name());
            stl.setOrderId(100L);
            when(settlementMapper.findById(1L)).thenReturn(stl);
            when(paymentMapper.findByOrderId(100L)).thenReturn(
                    buildPayment(1L, 100L, new BigDecimal("200.00"), PaymentStatus.FROZEN.name()));

            assertThatThrownBy(() -> escrowService.retrySettlement(1L, 1L))
                    .isInstanceOf(BizException.class);
            verify(settlementService, never()).retrySettlement(anyLong());
        }
    }
}
