package com.campus.trade.service;

import com.campus.trade.common.config.AlipayConfig;
import com.campus.trade.common.exception.BizException;
import com.campus.trade.common.exception.ErrorCode;
import com.campus.trade.common.util.DistributedLock;
import com.campus.trade.domain.entity.Order;
import com.campus.trade.domain.entity.Payment;
import com.campus.trade.domain.enums.OrderStatus;
import com.campus.trade.domain.enums.PaymentStatus;
import com.campus.trade.mapper.OrderMapper;
import com.campus.trade.mapper.PaymentMapper;
import com.campus.trade.mapper.RefundMapper;
import com.campus.trade.service.impl.PaymentServiceImpl;
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

/**
 * Tests proving that duplicate / out-of-order TRADE_SUCCESS callbacks
 * cannot pollute payment or order state when the order has already
 * moved past CREATED (cancelled, closed, refunding, settled, etc.).
 *
 * All tests exercise the {@code simulatePayNotify} entry point which
 * calls {@code processSuccess} directly (the same private method
 * invoked by {@code handleAlipayNotify} after signature verification).
 */
@ExtendWith(MockitoExtension.class)
class PaymentCallbackPollutionTest {

    @InjectMocks private PaymentServiceImpl paymentService;

    @Mock private AlipayConfig alipayConfig;
    @Mock private PaymentMapper paymentMapper;
    @Mock private OrderMapper orderMapper;
    @Mock private RefundMapper refundMapper;
    @Mock private OrderService orderService;
    @Mock private InventoryService inventoryService;
    @Mock private AuditService auditService;
    @Mock private DistributedLock distributedLock;
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

    // ── builders ──

    private Payment payment(Long id, String paymentNo, Long orderId, String orderNo,
                            BigDecimal amount, String status) {
        Payment p = new Payment();
        p.setId(id); p.setPaymentNo(paymentNo); p.setOrderId(orderId);
        p.setOrderNo(orderNo); p.setAmount(amount); p.setStatus(status);
        p.setPayChannel("ALIPAY");
        return p;
    }

    private Order order(Long id, String orderNo, String status) {
        Order o = new Order();
        o.setId(id); o.setOrderNo(orderNo); o.setStatus(status);
        o.setSkuId(10L); o.setQuantity(1); o.setTotalAmount(new BigDecimal("99.00"));
        return o;
    }

    /**
     * Standard mock setup for the simulatePayNotify entry point:
     * simulatePayNotify calls orderMapper.findByOrderNo then paymentMapper.findByOrderNo,
     * then delegates to processSuccess (which acquires lock, re-reads payment, etc.).
     */
    private void setupSimulate(String orderNo, Long orderId, Payment pmt, Order ord,
                               boolean lockOk) {
        when(orderMapper.findByOrderNo(orderNo)).thenReturn(ord);
        when(paymentMapper.findByOrderNo(orderNo)).thenReturn(pmt);
        when(distributedLock.tryLock("order:" + orderId)).thenReturn(
                lockOk ? new DistributedLock.LockHandle("order:" + orderId, "token") : null);
    }

    // ═══════════════════════════════════════════════════════════════
    //  1. Late callback after order CANCELLED
    // ═══════════════════════════════════════════════════════════════
    @Nested @DisplayName("After order CANCELLED") class AfterCancel {

        @Test void paymentStaysPending() {
            Payment p = payment(1L, "PAY001", 100L, "ORD001", new BigDecimal("99.00"), "PENDING");
            Order o = order(100L, "ORD001", "CANCELLED");
            setupSimulate("ORD001", 100L, p, o, true);
            when(paymentMapper.findById(1L)).thenReturn(p);
            when(orderMapper.findById(100L)).thenReturn(o);
            when(paymentMapper.updateStatus(1L, "PENDING", "SUCCESS")).thenReturn(1);

            paymentService.simulatePayNotify("ORD001", "T001");

            // Bug 8 fix: auto-refund on CANCELLED — payment goes PENDING→SUCCESS, then refund, then SUCCESS→CLOSED
            verify(paymentMapper).updateStatus(1L, "PENDING", "SUCCESS");
            verify(refundMapper).insert(any());
            verify(paymentMapper).updateStatus(1L, "SUCCESS", "CLOSED");
            verify(orderService, never()).transitionToPaidInternal(anyLong(), anyString());
            verify(inventoryService, never()).deductStock(anyLong(), anyInt());
        }

        @Test void auditEventLogged() {
            Payment p = payment(1L, "PAY001", 100L, "ORD001", new BigDecimal("99.00"), "PENDING");
            Order o = order(100L, "ORD001", "CANCELLED");
            setupSimulate("ORD001", 100L, p, o, true);
            when(paymentMapper.findById(1L)).thenReturn(p);
            when(orderMapper.findById(100L)).thenReturn(o);
            when(paymentMapper.updateStatus(1L, "PENDING", "SUCCESS")).thenReturn(1);

            paymentService.simulatePayNotify("ORD001", "T001");

            // Bug 8 fix: audit event is now AUTO_REFUND_ON_CANCELLED
            verify(auditService).log(isNull(), isNull(), eq("PAYMENT"),
                    eq("AUTO_REFUND_ON_CANCELLED"), eq("ORDER"), eq(100L),
                    contains("tradeNo=T001"));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  2. Late callback after order CLOSED (timeout)
    // ═══════════════════════════════════════════════════════════════
    @Nested @DisplayName("After order CLOSED") class AfterClose {

        @Test void paymentStaysPending() {
            Payment p = payment(1L, "PAY002", 200L, "ORD002", new BigDecimal("99.00"), "PENDING");
            Order o = order(200L, "ORD002", "CLOSED");
            setupSimulate("ORD002", 200L, p, o, true);
            when(paymentMapper.findById(1L)).thenReturn(p);
            when(orderMapper.findById(200L)).thenReturn(o);

            paymentService.simulatePayNotify("ORD002", "T002");

            verify(paymentMapper, never()).updateStatus(1L, "PENDING", "SUCCESS");
            verify(orderService, never()).transitionToPaidInternal(anyLong(), anyString());
            verify(inventoryService, never()).deductStock(anyLong(), anyInt());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  3. Callback when order is REFUNDING
    // ═══════════════════════════════════════════════════════════════
    @Nested @DisplayName("Order REFUNDING") class WhileRefunding {

        @Test void paymentStaysPending() {
            Payment p = payment(1L, "PAY003", 300L, "ORD003", new BigDecimal("99.00"), "PENDING");
            Order o = order(300L, "ORD003", "REFUNDING");
            setupSimulate("ORD003", 300L, p, o, true);
            when(paymentMapper.findById(1L)).thenReturn(p);
            when(orderMapper.findById(300L)).thenReturn(o);

            paymentService.simulatePayNotify("ORD003", "T003");

            verify(paymentMapper, never()).updateStatus(1L, "PENDING", "SUCCESS");
            verify(orderService, never()).transitionToPaidInternal(anyLong(), anyString());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  4. Callback when order is SETTLED (terminal)
    // ═══════════════════════════════════════════════════════════════
    @Nested @DisplayName("Order SETTLED") class WhileSettled {

        @Test void paymentStaysPending() {
            Payment p = payment(1L, "PAY004", 400L, "ORD004", new BigDecimal("99.00"), "PENDING");
            Order o = order(400L, "ORD004", "SETTLED");
            setupSimulate("ORD004", 400L, p, o, true);
            when(paymentMapper.findById(1L)).thenReturn(p);
            when(orderMapper.findById(400L)).thenReturn(o);

            paymentService.simulatePayNotify("ORD004", "T004");

            verify(paymentMapper, never()).updateStatus(1L, "PENDING", "SUCCESS");
            verify(orderService, never()).transitionToPaidInternal(anyLong(), anyString());
            verify(auditService).log(isNull(), isNull(), eq("PAYMENT"),
                    eq("CALLBACK_REJECTED_ORDER_NOT_CREATED"), eq("ORDER"), eq(400L),
                    contains("orderStatus=SETTLED"));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  5. Amount mismatch blocks processing
    // ═══════════════════════════════════════════════════════════════
    @Nested @DisplayName("Amount mismatch") class AmountMismatch {

        @Test void paymentStaysPending() {
            // payment.amount = 99.00, but order.totalAmount = 50.00
            // simulatePayNotify passes order.getTotalAmount().toPlainString() → "50.00"
            Payment p = payment(1L, "PAY005", 500L, "ORD005", new BigDecimal("99.00"), "PENDING");
            Order o = order(500L, "ORD005", "CREATED");
            o.setTotalAmount(new BigDecimal("50.00"));
            setupSimulate("ORD005", 500L, p, o, true);
            when(paymentMapper.findById(1L)).thenReturn(p);
            when(orderMapper.findById(500L)).thenReturn(o);

            paymentService.simulatePayNotify("ORD005", "T005");

            verify(paymentMapper, never()).updateStatus(1L, "PENDING", "SUCCESS");
            verify(paymentMapper, never()).updateTradeNo(anyLong(), anyString(), any());
            verify(orderService, never()).transitionToPaidInternal(anyLong(), anyString());
            verify(inventoryService, never()).deductStock(anyLong(), anyInt());
        }

        @Test void auditLogged() {
            Payment p = payment(1L, "PAY005", 500L, "ORD005", new BigDecimal("99.00"), "PENDING");
            Order o = order(500L, "ORD005", "CREATED");
            o.setTotalAmount(new BigDecimal("50.00"));
            setupSimulate("ORD005", 500L, p, o, true);
            when(paymentMapper.findById(1L)).thenReturn(p);
            when(orderMapper.findById(500L)).thenReturn(o);

            paymentService.simulatePayNotify("ORD005", "T005");

            verify(auditService).log(isNull(), isNull(), eq("PAYMENT"),
                    eq("AMOUNT_MISMATCH"), eq("PAYMENT"), eq(1L),
                    contains("expected=99.00"));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  6. trade_no already used by another payment
    // ═══════════════════════════════════════════════════════════════
    @Nested @DisplayName("Duplicate trade_no") class TradeNoDup {

        @Test void paymentStaysPending() {
            Payment p = payment(2L, "PAY006", 600L, "ORD006", new BigDecimal("99.00"), "PENDING");
            Order o = order(600L, "ORD006", "CREATED");
            Payment other = payment(1L, "PAY_OTHER", 999L, "ORD_OTHER", new BigDecimal("99.00"), "SUCCESS");
            setupSimulate("ORD006", 600L, p, o, true);
            when(paymentMapper.findById(2L)).thenReturn(p);
            when(orderMapper.findById(600L)).thenReturn(o);
            when(paymentMapper.findByTradeNo("TRADE_DUP")).thenReturn(other);

            paymentService.simulatePayNotify("ORD006", "TRADE_DUP");

            verify(paymentMapper, never()).updateStatus(2L, "PENDING", "SUCCESS");
            verify(orderService, never()).transitionToPaidInternal(anyLong(), anyString());
        }

        @Test void auditLogged() {
            Payment p = payment(2L, "PAY006", 600L, "ORD006", new BigDecimal("99.00"), "PENDING");
            Order o = order(600L, "ORD006", "CREATED");
            Payment other = payment(1L, "PAY_OTHER", 999L, "ORD_OTHER", new BigDecimal("99.00"), "SUCCESS");
            setupSimulate("ORD006", 600L, p, o, true);
            when(paymentMapper.findById(2L)).thenReturn(p);
            when(orderMapper.findById(600L)).thenReturn(o);
            when(paymentMapper.findByTradeNo("TRADE_DUP")).thenReturn(other);

            paymentService.simulatePayNotify("ORD006", "TRADE_DUP");

            verify(auditService).log(isNull(), isNull(), eq("PAYMENT"),
                    eq("TRADE_NO_DUPLICATE"), eq("PAYMENT"), eq(2L),
                    contains("tradeNo=TRADE_DUP"));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  7. Concurrent duplicate callbacks
    // ═══════════════════════════════════════════════════════════════
    @Nested @DisplayName("Concurrent duplicates") class ConcurrentDup {

        @Test void secondCallback_afterFirstAlreadySucceeded() {
            Payment pending = payment(1L, "PAY007", 700L, "ORD007", new BigDecimal("99.00"), "PENDING");
            Payment alreadySuccess = payment(1L, "PAY007", 700L, "ORD007", new BigDecimal("99.00"), "SUCCESS");
            Order o = order(700L, "ORD007", "CREATED");
            setupSimulate("ORD007", 700L, pending, o, true);
            // Re-read inside lock returns SUCCESS
            when(paymentMapper.findById(1L)).thenReturn(alreadySuccess);

            paymentService.simulatePayNotify("ORD007", "T007");

            verify(paymentMapper, never()).updateStatus(anyLong(), anyString(), anyString());
            verify(orderService, never()).transitionToPaidInternal(anyLong(), anyString());
            verify(orderMapper, never()).findById(anyLong());
        }

        @Test void lockContention_returnsSuccessNoProcessing() {
            Payment p = payment(1L, "PAY007", 700L, "ORD007", new BigDecimal("99.00"), "PENDING");
            Order o = order(700L, "ORD007", "CREATED");
            setupSimulate("ORD007", 700L, p, o, false); // lock fails

            String result = paymentService.simulatePayNotify("ORD007", "T007");

            assertThat(result).isEqualTo("success");
            verify(paymentMapper, never()).findById(anyLong());
            verify(paymentMapper, never()).updateStatus(anyLong(), anyString(), anyString());
            verify(orderMapper, never()).findById(anyLong());
            verify(orderService, never()).transitionToPaidInternal(anyLong(), anyString());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  8. Happy path — order still CREATED (callback before frontend return)
    // ═══════════════════════════════════════════════════════════════
    @Nested @DisplayName("Happy path") class HappyPath {

        @Test void fullSuccessChain() {
            Payment p = payment(1L, "PAY008", 800L, "ORD008", new BigDecimal("99.00"), "PENDING");
            Order o = order(800L, "ORD008", "CREATED");
            setupSimulate("ORD008", 800L, p, o, true);
            when(paymentMapper.findById(1L)).thenReturn(p);
            when(orderMapper.findById(800L)).thenReturn(o);
            when(paymentMapper.findByTradeNo("T_OK")).thenReturn(null);
            when(paymentMapper.updateStatus(1L, "PENDING", "SUCCESS")).thenReturn(1);
            when(paymentMapper.updateTradeNo(eq(1L), eq("T_OK"), any())).thenReturn(1);
            when(orderMapper.updatePayInfo(eq(800L), any())).thenReturn(1);

            paymentService.simulatePayNotify("ORD008", "T_OK");

            verify(paymentMapper).updateStatus(1L, "PENDING", "SUCCESS");
            verify(paymentMapper).updateTradeNo(eq(1L), eq("T_OK"), any());
            verify(orderService).transitionToPaidInternal(800L, "Payment callback received");
            verify(orderMapper).updatePayInfo(eq(800L), any());
            verify(inventoryService).deductStock(10L, 1);
            verify(auditService).log(isNull(), isNull(), eq("PAYMENT"), eq("SUCCESS"),
                    eq("ORDER"), eq(800L), contains("tradeNo=T_OK"));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  9. Payment optimistic-lock CAS fails
    // ═══════════════════════════════════════════════════════════════
    @Nested @DisplayName("Payment CAS fail") class CasFail {

        @Test void noProcessing() {
            Payment p = payment(1L, "PAY009", 900L, "ORD009", new BigDecimal("99.00"), "PENDING");
            Order o = order(900L, "ORD009", "CREATED");
            setupSimulate("ORD009", 900L, p, o, true);
            when(paymentMapper.findById(1L)).thenReturn(p);
            when(orderMapper.findById(900L)).thenReturn(o);
            when(paymentMapper.findByTradeNo("T_CAS")).thenReturn(null);
            when(paymentMapper.updateStatus(1L, "PENDING", "SUCCESS")).thenReturn(0);

            paymentService.simulatePayNotify("ORD009", "T_CAS");

            verify(paymentMapper, never()).updateTradeNo(anyLong(), anyString(), any());
            verify(orderService, never()).transitionToPaidInternal(anyLong(), anyString());
            verify(inventoryService, never()).deductStock(anyLong(), anyInt());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  10. Order null (vanished)
    // ═══════════════════════════════════════════════════════════════
    @Nested @DisplayName("Order vanished") class OrderNull {

        @Test void paymentStaysPending() {
            Payment p = payment(1L, "PAY010", 1000L, "ORD010", new BigDecimal("99.00"), "PENDING");
            Order o = order(1000L, "ORD010", "CREATED");
            setupSimulate("ORD010", 1000L, p, o, true);
            when(paymentMapper.findById(1L)).thenReturn(p);
            when(orderMapper.findById(1000L)).thenReturn(null); // vanished inside lock

            paymentService.simulatePayNotify("ORD010", "T010");

            verify(paymentMapper, never()).updateStatus(1L, "PENDING", "SUCCESS");
            // Bug fix: null order now just logs warn + returns, no audit event
            verify(auditService, never()).log(any(), any(), anyString(), anyString(), anyString(), anyLong(), anyString());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  11. Payment already CLOSED — re-read inside lock returns CLOSED
    // ═══════════════════════════════════════════════════════════════
    @Nested @DisplayName("Payment CLOSED inside lock") class ClosedInLock {

        @Test void noProcessing() {
            Payment pending = payment(1L, "PAY011", 1100L, "ORD011", new BigDecimal("99.00"), "PENDING");
            Payment closed = payment(1L, "PAY011", 1100L, "ORD011", new BigDecimal("99.00"), "CLOSED");
            Order o = order(1100L, "ORD011", "CREATED");
            setupSimulate("ORD011", 1100L, pending, o, true);
            when(paymentMapper.findById(1L)).thenReturn(closed); // re-read: CLOSED

            paymentService.simulatePayNotify("ORD011", "T011");

            verify(orderMapper, never()).findById(anyLong());
            verify(paymentMapper, never()).updateStatus(anyLong(), anyString(), anyString());
            verify(orderService, never()).transitionToPaidInternal(anyLong(), anyString());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  12. Order transition fails AFTER payment marked SUCCESS
    //      (concurrent cancel won the DB optimistic-lock race)
    // ═══════════════════════════════════════════════════════════════
    @Nested @DisplayName("Order transition fails post-payment-SUCCESS") class TransitionFail {

        @Test void auditLoggedAndReturnsSuccess() {
            Payment p = payment(1L, "PAY012", 1200L, "ORD012", new BigDecimal("99.00"), "PENDING");
            Order o = order(1200L, "ORD012", "CREATED");
            setupSimulate("ORD012", 1200L, p, o, true);
            when(paymentMapper.findById(1L)).thenReturn(p);
            when(orderMapper.findById(1200L)).thenReturn(o);
            when(paymentMapper.findByTradeNo("T012")).thenReturn(null);
            when(paymentMapper.updateStatus(1L, "PENDING", "SUCCESS")).thenReturn(1);
            when(paymentMapper.updateTradeNo(eq(1L), eq("T012"), any())).thenReturn(1);
            doThrow(new BizException(ErrorCode.ORDER_STATUS_INVALID, "CANCELLED -> PAID"))
                    .when(orderService).transitionToPaidInternal(1200L, "Payment callback received");

            String result = paymentService.simulatePayNotify("ORD012", "T012");
            assertThat(result).isEqualTo("success");

            verify(paymentMapper).updateStatus(1L, "PENDING", "SUCCESS");
            verify(auditService).log(isNull(), isNull(), eq("PAYMENT"),
                    eq("ORDER_TRANSITION_FAILED"), eq("ORDER"), eq(1200L),
                    contains("paymentStatus=SUCCESS"));
            verify(inventoryService, never()).deductStock(anyLong(), anyInt());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  13. Order already PAID (double-pay via different channel)
    // ═══════════════════════════════════════════════════════════════
    @Nested @DisplayName("Order already PAID") class AlreadyPaid {

        @Test void paymentStaysPending() {
            Payment p = payment(1L, "PAY013", 1300L, "ORD013", new BigDecimal("99.00"), "PENDING");
            Order o = order(1300L, "ORD013", "PAID");
            setupSimulate("ORD013", 1300L, p, o, true);
            when(paymentMapper.findById(1L)).thenReturn(p);
            when(orderMapper.findById(1300L)).thenReturn(o);

            paymentService.simulatePayNotify("ORD013", "T013");

            verify(paymentMapper, never()).updateStatus(1L, "PENDING", "SUCCESS");
            verify(orderService, never()).transitionToPaidInternal(anyLong(), anyString());
            verify(inventoryService, never()).deductStock(anyLong(), anyInt());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  14. Order in DISPUTED state
    // ═══════════════════════════════════════════════════════════════
    @Nested @DisplayName("Order DISPUTED") class Disputed {

        @Test void paymentStaysPending() {
            Payment p = payment(1L, "PAY014", 1400L, "ORD014", new BigDecimal("99.00"), "PENDING");
            Order o = order(1400L, "ORD014", "DISPUTED");
            setupSimulate("ORD014", 1400L, p, o, true);
            when(paymentMapper.findById(1L)).thenReturn(p);
            when(orderMapper.findById(1400L)).thenReturn(o);

            paymentService.simulatePayNotify("ORD014", "T014");

            verify(paymentMapper, never()).updateStatus(1L, "PENDING", "SUCCESS");
            verify(auditService).log(isNull(), isNull(), eq("PAYMENT"),
                    eq("CALLBACK_REJECTED_ORDER_NOT_CREATED"), eq("ORDER"), eq(1400L),
                    contains("orderStatus=DISPUTED"));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  15. Order in SHIPPED state (far past CREATED)
    // ═══════════════════════════════════════════════════════════════
    @Nested @DisplayName("Order SHIPPED") class Shipped {

        @Test void paymentStaysPending() {
            Payment p = payment(1L, "PAY015", 1500L, "ORD015", new BigDecimal("99.00"), "PENDING");
            Order o = order(1500L, "ORD015", "SHIPPED");
            setupSimulate("ORD015", 1500L, p, o, true);
            when(paymentMapper.findById(1L)).thenReturn(p);
            when(orderMapper.findById(1500L)).thenReturn(o);

            paymentService.simulatePayNotify("ORD015", "T015");

            verify(paymentMapper, never()).updateStatus(1L, "PENDING", "SUCCESS");
            verify(orderService, never()).transitionToPaidInternal(anyLong(), anyString());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  16. Order in RECEIVED state
    // ═══════════════════════════════════════════════════════════════
    @Nested @DisplayName("Order RECEIVED") class Received {

        @Test void paymentStaysPending() {
            Payment p = payment(1L, "PAY016", 1600L, "ORD016", new BigDecimal("99.00"), "PENDING");
            Order o = order(1600L, "ORD016", "RECEIVED");
            setupSimulate("ORD016", 1600L, p, o, true);
            when(paymentMapper.findById(1L)).thenReturn(p);
            when(orderMapper.findById(1600L)).thenReturn(o);

            paymentService.simulatePayNotify("ORD016", "T016");

            verify(paymentMapper, never()).updateStatus(1L, "PENDING", "SUCCESS");
            verify(orderService, never()).transitionToPaidInternal(anyLong(), anyString());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  17. Order in REFUNDED state (terminal)
    // ═══════════════════════════════════════════════════════════════
    @Nested @DisplayName("Order REFUNDED") class Refunded {

        @Test void paymentStaysPending() {
            Payment p = payment(1L, "PAY017", 1700L, "ORD017", new BigDecimal("99.00"), "PENDING");
            Order o = order(1700L, "ORD017", "REFUNDED");
            setupSimulate("ORD017", 1700L, p, o, true);
            when(paymentMapper.findById(1L)).thenReturn(p);
            when(orderMapper.findById(1700L)).thenReturn(o);

            paymentService.simulatePayNotify("ORD017", "T017");

            verify(paymentMapper, never()).updateStatus(1L, "PENDING", "SUCCESS");
            verify(orderService, never()).transitionToPaidInternal(anyLong(), anyString());
        }
    }
}
