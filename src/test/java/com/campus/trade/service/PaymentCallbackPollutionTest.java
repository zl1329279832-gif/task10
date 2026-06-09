package com.campus.trade.service;

import com.campus.trade.common.config.AlipayConfig;
import com.campus.trade.common.exception.BizException;
import com.campus.trade.common.util.DistributedLock;
import com.campus.trade.domain.entity.Order;
import com.campus.trade.domain.entity.Payment;
import com.campus.trade.domain.enums.OrderStatus;
import com.campus.trade.domain.enums.PaymentStatus;
import com.campus.trade.mapper.OrderMapper;
import com.campus.trade.mapper.OrderStatusLogMapper;
import com.campus.trade.mapper.PaymentMapper;
import com.campus.trade.service.impl.PaymentServiceImpl;
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
 * Verifies that TRADE_SUCCESS callbacks arriving after order closure / in terminal states
 * never pollute payment or order status. Only audit records are produced.
 */
@ExtendWith(MockitoExtension.class)
class PaymentCallbackPollutionTest {

    @InjectMocks private PaymentServiceImpl paymentService;
    @Mock private AlipayConfig alipayConfig;
    @Mock private PaymentMapper paymentMapper;
    @Mock private OrderMapper orderMapper;
    @Mock private OrderService orderService;
    @Mock private InventoryService inventoryService;
    @Mock private AuditService auditService;
    @Mock private DistributedLock distributedLock;
    @Mock private OrderStatusLogMapper orderStatusLogMapper;

    private Payment pendingPayment() {
        Payment p = new Payment();
        p.setId(1L);
        p.setPaymentNo("PAY_001");
        p.setOrderId(100L);
        p.setOrderNo("ORD_001");
        p.setAmount(new BigDecimal("100.00"));
        p.setStatus(PaymentStatus.PENDING.name());
        p.setNotifyCount(0);
        return p;
    }

    private Order orderWithStatus(String status) {
        Order o = new Order();
        o.setId(100L);
        o.setOrderNo("ORD_001");
        o.setBuyerId(1L);
        o.setSellerId(2L);
        o.setProductId(10L);
        o.setSkuId(1L);
        o.setQuantity(1);
        o.setUnitPrice(new BigDecimal("100.00"));
        o.setTotalAmount(new BigDecimal("100.00"));
        o.setStatus(status);
        o.setPayExpireAt(LocalDateTime.now().plusMinutes(30));
        return o;
    }

    /** Common setup: order lookup by orderNo + payment lookup by orderNo for simulatePayNotify */
    private void stubSimulate(Order order, Payment payment) {
        when(orderMapper.findByOrderNo("ORD_001")).thenReturn(order);
        when(paymentMapper.findByOrderNo("ORD_001")).thenReturn(payment);
    }

    /** Common setup for processSuccess path: trade_no not duplicate, order lock acquired */
    private void stubProcessSuccess(Order order) {
        lenient().when(paymentMapper.findByTradeNo(anyString())).thenReturn(null);
        lenient().when(distributedLock.tryLock("order:100")).thenReturn(true);
        lenient().when(orderMapper.findById(100L)).thenReturn(order);
    }

    // ==================== Happy Path ====================

    @Nested @DisplayName("HappyPath")
    class HappyPath {
        @Test @DisplayName("full success chain: payment PENDING->SUCCESS, order CREATED->PAID, stock deducted")
        void fullSuccessChain() {
            Order order = orderWithStatus("CREATED");
            Payment payment = pendingPayment();
            stubSimulate(order, payment);
            stubProcessSuccess(order);

            when(orderMapper.updateStatus(100L, "CREATED", "PAID")).thenReturn(1);
            when(paymentMapper.updateStatus(1L, "PENDING", "SUCCESS")).thenReturn(1);
            when(paymentMapper.updateTradeNo(eq(1L), eq("T001"), any())).thenReturn(1);
            when(orderMapper.updatePayInfo(eq(100L), any())).thenReturn(1);
            when(orderStatusLogMapper.insert(any())).thenReturn(1);

            String result = paymentService.simulatePayNotify("ORD_001", "T001");

            assertThat(result).isEqualTo("success");
            verify(orderMapper).updateStatus(100L, "CREATED", "PAID");
            verify(paymentMapper).updateStatus(1L, "PENDING", "SUCCESS");
            verify(inventoryService).deductStock(1L, 1);
            verify(auditService).log(isNull(), isNull(), eq("PAYMENT"), eq("SUCCESS"), eq("ORDER"), eq(100L), contains("T001"));
            verify(distributedLock).unlock("order:100");
        }
    }

    // ==================== Already Paid (payment already SUCCESS) ====================

    @Nested @DisplayName("AlreadyPaid")
    class AlreadyPaid {
        @Test @DisplayName("payment already SUCCESS -> CAS returns 0, idempotent return")
        void paymentAlreadySuccess() {
            Order order = orderWithStatus("CREATED");
            Payment payment = pendingPayment();
            stubSimulate(order, payment);
            stubProcessSuccess(order);

            when(orderMapper.updateStatus(100L, "CREATED", "PAID")).thenReturn(1);
            when(paymentMapper.updateStatus(1L, "PENDING", "SUCCESS")).thenReturn(0); // already processed

            String result = paymentService.simulatePayNotify("ORD_001", "T001");

            assertThat(result).isEqualTo("success");
            verify(inventoryService, never()).deductStock(anyLong(), anyInt());
        }
    }

    // ==================== Order Terminal States: Callback must NOT pollute ====================

    @Nested @DisplayName("AfterCancel")
    class AfterCancel {
        @Test @DisplayName("order CANCELLED -> payment stays PENDING, audit logged")
        void callbackAfterCancel() {
            Order order = orderWithStatus("CANCELLED");
            Payment payment = pendingPayment();
            stubSimulate(order, payment);
            stubProcessSuccess(order);

            String result = paymentService.simulatePayNotify("ORD_001", "T001");

            assertThat(result).isEqualTo("success");
            verify(paymentMapper, never()).updateStatus(anyLong(), anyString(), anyString());
            verify(orderMapper, never()).updateStatus(anyLong(), anyString(), anyString());
            verify(inventoryService, never()).deductStock(anyLong(), anyInt());
            verify(auditService).log(isNull(), isNull(), eq("PAYMENT"), eq("CALLBACK_REJECTED"), eq("ORDER"), eq(100L),
                    contains("CANCELLED"));
        }
    }

    @Nested @DisplayName("AfterClose")
    class AfterClose {
        @Test @DisplayName("order CLOSED -> payment stays PENDING, audit logged")
        void callbackAfterClose() {
            Order order = orderWithStatus("CLOSED");
            Payment payment = pendingPayment();
            stubSimulate(order, payment);
            stubProcessSuccess(order);

            String result = paymentService.simulatePayNotify("ORD_001", "T001");

            assertThat(result).isEqualTo("success");
            verify(paymentMapper, never()).updateStatus(anyLong(), anyString(), anyString());
            verify(orderMapper, never()).updateStatus(anyLong(), anyString(), anyString());
            verify(auditService).log(isNull(), isNull(), eq("PAYMENT"), eq("CALLBACK_REJECTED"), eq("ORDER"), eq(100L),
                    contains("CLOSED"));
        }
    }

    @Nested @DisplayName("Shipped")
    class Shipped {
        @Test @DisplayName("order SHIPPED -> payment stays PENDING, audit logged")
        void callbackAfterShipped() {
            Order order = orderWithStatus("SHIPPED");
            Payment payment = pendingPayment();
            stubSimulate(order, payment);
            stubProcessSuccess(order);

            String result = paymentService.simulatePayNotify("ORD_001", "T001");

            assertThat(result).isEqualTo("success");
            verify(paymentMapper, never()).updateStatus(anyLong(), anyString(), anyString());
            verify(auditService).log(isNull(), isNull(), eq("PAYMENT"), eq("CALLBACK_REJECTED"), eq("ORDER"), eq(100L),
                    contains("SHIPPED"));
        }
    }

    @Nested @DisplayName("Received")
    class Received {
        @Test @DisplayName("order RECEIVED -> payment stays PENDING, audit logged")
        void callbackAfterReceived() {
            Order order = orderWithStatus("RECEIVED");
            Payment payment = pendingPayment();
            stubSimulate(order, payment);
            stubProcessSuccess(order);

            String result = paymentService.simulatePayNotify("ORD_001", "T001");

            assertThat(result).isEqualTo("success");
            verify(paymentMapper, never()).updateStatus(anyLong(), anyString(), anyString());
            verify(auditService).log(isNull(), isNull(), eq("PAYMENT"), eq("CALLBACK_REJECTED"), eq("ORDER"), eq(100L),
                    contains("RECEIVED"));
        }
    }

    @Nested @DisplayName("Refunded")
    class Refunded {
        @Test @DisplayName("order REFUNDED -> payment stays PENDING, audit logged")
        void callbackAfterRefunded() {
            Order order = orderWithStatus("REFUNDED");
            Payment payment = pendingPayment();
            stubSimulate(order, payment);
            stubProcessSuccess(order);

            String result = paymentService.simulatePayNotify("ORD_001", "T001");

            assertThat(result).isEqualTo("success");
            verify(paymentMapper, never()).updateStatus(anyLong(), anyString(), anyString());
            verify(auditService).log(isNull(), isNull(), eq("PAYMENT"), eq("CALLBACK_REJECTED"), eq("ORDER"), eq(100L),
                    contains("REFUNDED"));
        }
    }

    @Nested @DisplayName("Disputed")
    class Disputed {
        @Test @DisplayName("order DISPUTED -> payment stays PENDING, audit logged")
        void callbackAfterDisputed() {
            Order order = orderWithStatus("DISPUTED");
            Payment payment = pendingPayment();
            stubSimulate(order, payment);
            stubProcessSuccess(order);

            String result = paymentService.simulatePayNotify("ORD_001", "T001");

            assertThat(result).isEqualTo("success");
            verify(paymentMapper, never()).updateStatus(anyLong(), anyString(), anyString());
            verify(auditService).log(isNull(), isNull(), eq("PAYMENT"), eq("CALLBACK_REJECTED"), eq("ORDER"), eq(100L),
                    contains("DISPUTED"));
        }
    }

    @Nested @DisplayName("WhileRefunding")
    class WhileRefunding {
        @Test @DisplayName("order REFUNDING -> payment stays PENDING, audit logged")
        void callbackWhileRefunding() {
            Order order = orderWithStatus("REFUNDING");
            Payment payment = pendingPayment();
            stubSimulate(order, payment);
            stubProcessSuccess(order);

            String result = paymentService.simulatePayNotify("ORD_001", "T001");

            assertThat(result).isEqualTo("success");
            verify(paymentMapper, never()).updateStatus(anyLong(), anyString(), anyString());
            verify(auditService).log(isNull(), isNull(), eq("PAYMENT"), eq("CALLBACK_REJECTED"), eq("ORDER"), eq(100L),
                    contains("REFUNDING"));
        }
    }

    @Nested @DisplayName("WhileSettled")
    class WhileSettled {
        @Test @DisplayName("order SETTLED -> payment stays PENDING, audit logged")
        void callbackWhileSettled() {
            Order order = orderWithStatus("SETTLED");
            Payment payment = pendingPayment();
            stubSimulate(order, payment);
            stubProcessSuccess(order);

            String result = paymentService.simulatePayNotify("ORD_001", "T001");

            assertThat(result).isEqualTo("success");
            verify(paymentMapper, never()).updateStatus(anyLong(), anyString(), anyString());
            verify(auditService).log(isNull(), isNull(), eq("PAYMENT"), eq("CALLBACK_REJECTED"), eq("ORDER"), eq(100L),
                    contains("SETTLED"));
        }
    }

    // ==================== Order Not Found ====================

    @Nested @DisplayName("OrderNull")
    class OrderNull {
        @Test @DisplayName("order not found -> payment stays PENDING, audit logged")
        void orderNotFound() {
            Order order = orderWithStatus("CREATED");
            Payment payment = pendingPayment();
            stubSimulate(order, payment);

            when(paymentMapper.findByTradeNo(anyString())).thenReturn(null);
            when(distributedLock.tryLock("order:100")).thenReturn(true);
            when(orderMapper.findById(100L)).thenReturn(null);

            String result = paymentService.simulatePayNotify("ORD_001", "T001");

            assertThat(result).isEqualTo("success");
            verify(paymentMapper, never()).updateStatus(anyLong(), anyString(), anyString());
            verify(auditService).log(isNull(), isNull(), eq("PAYMENT"), eq("CALLBACK_REJECTED_ORDER_NULL"),
                    eq("PAYMENT"), eq(1L), contains("orderId=100"));
        }
    }

    // ==================== CAS Failures ====================

    @Nested @DisplayName("CasFail")
    class CasFail {
        @Test @DisplayName("order CAS fails (concurrent modification) -> payment stays PENDING")
        void orderCasFails() {
            Order order = orderWithStatus("CREATED");
            Payment payment = pendingPayment();
            stubSimulate(order, payment);
            stubProcessSuccess(order);

            when(orderMapper.updateStatus(100L, "CREATED", "PAID")).thenReturn(0); // concurrent modification

            String result = paymentService.simulatePayNotify("ORD_001", "T001");

            assertThat(result).isEqualTo("success");
            verify(paymentMapper, never()).updateStatus(anyLong(), anyString(), anyString());
            verify(auditService).log(isNull(), isNull(), eq("PAYMENT"), eq("CALLBACK_REJECTED_CONCURRENT"),
                    eq("ORDER"), eq(100L), anyString());
        }
    }

    @Nested @DisplayName("PostTransitionFail")
    class PostTransitionFail {
        @Test @DisplayName("order transition succeeds but payment CAS fails -> audit logged")
        void orderTransitionSucceeds_paymentCasFails() {
            Order order = orderWithStatus("CREATED");
            Payment payment = pendingPayment();
            stubSimulate(order, payment);
            stubProcessSuccess(order);

            when(orderMapper.updateStatus(100L, "CREATED", "PAID")).thenReturn(1);
            when(paymentMapper.updateStatus(1L, "PENDING", "SUCCESS")).thenReturn(0); // already processed

            String result = paymentService.simulatePayNotify("ORD_001", "T001");

            assertThat(result).isEqualTo("success");
            verify(inventoryService, never()).deductStock(anyLong(), anyInt());
            verify(auditService).log(isNull(), isNull(), eq("PAYMENT"), eq("PAYMENT_CAS_AFTER_ORDER"),
                    eq("PAYMENT"), eq(1L), contains("T001"));
        }
    }

    // ==================== Amount Mismatch ====================

    @Nested @DisplayName("AmountMismatch")
    class AmountMismatch {
        @Test @DisplayName("amount mismatch -> payment stays PENDING")
        void paymentStaysPending() {
            Order order = orderWithStatus("CREATED");
            order.setTotalAmount(new BigDecimal("999.99")); // mismatch vs payment 100.00
            Payment payment = pendingPayment();
            stubSimulate(order, payment);

            String result = paymentService.simulatePayNotify("ORD_001", "T001");

            assertThat(result).isEqualTo("success");
            verify(paymentMapper, never()).updateStatus(anyLong(), anyString(), anyString());
            verify(orderMapper, never()).updateStatus(anyLong(), anyString(), anyString());
            verify(inventoryService, never()).deductStock(anyLong(), anyInt());
        }

        @Test @DisplayName("amount mismatch -> audit logged with details")
        void auditLogged() {
            Order order = orderWithStatus("CREATED");
            order.setTotalAmount(new BigDecimal("999.99"));
            Payment payment = pendingPayment();
            stubSimulate(order, payment);

            paymentService.simulatePayNotify("ORD_001", "T001");

            verify(auditService).log(isNull(), isNull(), eq("PAYMENT"), eq("AMOUNT_MISMATCH"),
                    eq("PAYMENT"), eq(1L), argThat(s -> s.contains("expected=100.00") && s.contains("actual=999.99")));
        }
    }

    // ==================== Trade No Duplicate ====================

    @Nested @DisplayName("TradeNoDup")
    class TradeNoDup {
        @Test @DisplayName("tradeNo already used by another payment -> payment stays PENDING")
        void tradeNoAlreadyUsed_paymentStaysPending() {
            Order order = orderWithStatus("CREATED");
            Payment payment = pendingPayment();
            stubSimulate(order, payment);

            Payment otherPayment = new Payment();
            otherPayment.setId(999L);
            otherPayment.setPaymentNo("PAY_OTHER");
            when(paymentMapper.findByTradeNo("T001")).thenReturn(otherPayment);

            String result = paymentService.simulatePayNotify("ORD_001", "T001");

            assertThat(result).isEqualTo("success");
            verify(paymentMapper, never()).updateStatus(anyLong(), anyString(), anyString());
            verify(orderMapper, never()).updateStatus(anyLong(), anyString(), anyString());
        }

        @Test @DisplayName("tradeNo duplicate -> audit logged with existing payment id")
        void tradeNoDuplicate_auditLogged() {
            Order order = orderWithStatus("CREATED");
            Payment payment = pendingPayment();
            stubSimulate(order, payment);

            Payment otherPayment = new Payment();
            otherPayment.setId(999L);
            otherPayment.setPaymentNo("PAY_OTHER");
            when(paymentMapper.findByTradeNo("T001")).thenReturn(otherPayment);

            paymentService.simulatePayNotify("ORD_001", "T001");

            verify(auditService).log(isNull(), isNull(), eq("PAYMENT"), eq("TRADE_NO_DUPLICATE"),
                    eq("PAYMENT"), eq(1L), argThat(s -> s.contains("tradeNo=T001") && s.contains("existingPaymentId=999")));
        }
    }

    // ==================== Concurrent Callbacks ====================

    @Nested @DisplayName("ConcurrentDup")
    class ConcurrentDup {
        @Test @DisplayName("second callback after first already succeeded -> order/payment not touched again")
        void secondCallback_afterFirstAlreadySucceeded() {
            Order order = orderWithStatus("CREATED");
            Payment payment = pendingPayment();
            stubSimulate(order, payment);
            stubProcessSuccess(order);

            when(orderMapper.updateStatus(100L, "CREATED", "PAID")).thenReturn(1);
            when(paymentMapper.updateStatus(1L, "PENDING", "SUCCESS")).thenReturn(1);
            when(paymentMapper.updateTradeNo(eq(1L), eq("T001"), any())).thenReturn(1);
            when(orderMapper.updatePayInfo(eq(100L), any())).thenReturn(1);
            when(orderStatusLogMapper.insert(any())).thenReturn(1);

            // First callback succeeds
            String r1 = paymentService.simulatePayNotify("ORD_001", "T001");
            assertThat(r1).isEqualTo("success");

            // Second callback: order is now PAID, not CREATED
            Order paidOrder = orderWithStatus("PAID");
            when(orderMapper.findById(100L)).thenReturn(paidOrder);

            String r2 = paymentService.simulatePayNotify("ORD_001", "T001");
            assertThat(r2).isEqualTo("success");

            // Order and payment CAS should only have been called once
            verify(orderMapper, times(1)).updateStatus(100L, "CREATED", "PAID");
            verify(paymentMapper, times(1)).updateStatus(1L, "PENDING", "SUCCESS");
            verify(inventoryService, times(1)).deductStock(1L, 1);
        }

        @Test @DisplayName("order lock contention -> returns failure for Alipay retry")
        void lockContention_returnsFailure() {
            Order order = orderWithStatus("CREATED");
            Payment payment = pendingPayment();
            stubSimulate(order, payment);

            when(paymentMapper.findByTradeNo(anyString())).thenReturn(null);
            when(distributedLock.tryLock("order:100")).thenReturn(false); // lock contention

            String result = paymentService.simulatePayNotify("ORD_001", "T001");

            assertThat(result).isEqualTo("failure");
            verify(paymentMapper, never()).updateStatus(anyLong(), anyString(), anyString());
            verify(orderMapper, never()).updateStatus(anyLong(), anyString(), anyString());
        }
    }

    // ==================== Closed Payment Fast Path ====================

    @Nested @DisplayName("ClosedFastPath")
    class ClosedFastPath {
        @Test @DisplayName("CLOSED payment receives callback via simulate -> amount mismatch path blocks")
        void closedPayment_simulateBlocked() {
            Order order = orderWithStatus("CREATED");
            Payment payment = pendingPayment();
            payment.setStatus(PaymentStatus.CLOSED.name());
            stubSimulate(order, payment);

            // processSuccess is called with CLOSED payment but the amount check happens first
            // Since the payment is CLOSED, CAS PENDING->SUCCESS will return 0
            when(paymentMapper.findByTradeNo(anyString())).thenReturn(null);
            when(distributedLock.tryLock("order:100")).thenReturn(true);
            when(orderMapper.findById(100L)).thenReturn(order);
            when(orderMapper.updateStatus(100L, "CREATED", "PAID")).thenReturn(1);
            when(paymentMapper.updateStatus(1L, "PENDING", "SUCCESS")).thenReturn(0); // CLOSED != PENDING

            String result = paymentService.simulatePayNotify("ORD_001", "T001");

            assertThat(result).isEqualTo("success");
            // Stock should NOT be deducted since payment CAS failed
            verify(inventoryService, never()).deductStock(anyLong(), anyInt());
        }
    }

    // ==================== Order Lock Released ====================

    @Nested @DisplayName("LockCleanup")
    class LockCleanup {
        @Test @DisplayName("order lock is always released even on rejection")
        void lockReleasedOnRejection() {
            Order order = orderWithStatus("CANCELLED");
            Payment payment = pendingPayment();
            stubSimulate(order, payment);
            stubProcessSuccess(order);

            paymentService.simulatePayNotify("ORD_001", "T001");

            verify(distributedLock).unlock("order:100");
        }

        @Test @DisplayName("order lock is released on success path")
        void lockReleasedOnSuccess() {
            Order order = orderWithStatus("CREATED");
            Payment payment = pendingPayment();
            stubSimulate(order, payment);
            stubProcessSuccess(order);

            when(orderMapper.updateStatus(100L, "CREATED", "PAID")).thenReturn(1);
            when(paymentMapper.updateStatus(1L, "PENDING", "SUCCESS")).thenReturn(1);
            when(paymentMapper.updateTradeNo(eq(1L), anyString(), any())).thenReturn(1);
            when(orderMapper.updatePayInfo(eq(100L), any())).thenReturn(1);
            when(orderStatusLogMapper.insert(any())).thenReturn(1);

            paymentService.simulatePayNotify("ORD_001", "T001");

            verify(distributedLock).unlock("order:100");
        }
    }

    // ==================== TradeNo same payment (not duplicate) ====================

    @Nested @DisplayName("TradeNoSamePayment")
    class TradeNoSamePayment {
        @Test @DisplayName("tradeNo already recorded for THIS payment -> allowed to proceed")
        void tradeNoSamePayment_allowed() {
            Order order = orderWithStatus("CREATED");
            Payment payment = pendingPayment();
            stubSimulate(order, payment);

            // findByTradeNo returns the same payment (retry scenario)
            when(paymentMapper.findByTradeNo("T001")).thenReturn(payment);
            when(distributedLock.tryLock("order:100")).thenReturn(true);
            when(orderMapper.findById(100L)).thenReturn(order);
            when(orderMapper.updateStatus(100L, "CREATED", "PAID")).thenReturn(1);
            when(paymentMapper.updateStatus(1L, "PENDING", "SUCCESS")).thenReturn(1);
            when(paymentMapper.updateTradeNo(eq(1L), eq("T001"), any())).thenReturn(1);
            when(orderMapper.updatePayInfo(eq(100L), any())).thenReturn(1);
            when(orderStatusLogMapper.insert(any())).thenReturn(1);

            String result = paymentService.simulatePayNotify("ORD_001", "T001");

            assertThat(result).isEqualTo("success");
            verify(paymentMapper).updateStatus(1L, "PENDING", "SUCCESS");
            verify(orderMapper).updateStatus(100L, "CREATED", "PAID");
        }
    }

    // ==================== All terminal states comprehensive ====================

    @Nested @DisplayName("AllTerminalStates")
    class AllTerminalStates {
        @Test @DisplayName("no terminal/non-CREATED state allows payment or order advancement")
        void noTerminalStateAllowsAdvancement() {
            String[] blockedStatuses = {
                "CANCELLED", "CLOSED", "SHIPPED", "RECEIVED", "SETTLED",
                "REFUNDING", "REFUNDED", "DISPUTED", "PAID"
            };
            for (String status : blockedStatuses) {
                Mockito.clearInvocations(paymentMapper, orderMapper, inventoryService, auditService, distributedLock);

                Order order = orderWithStatus(status);
                Payment payment = pendingPayment();
                when(orderMapper.findByOrderNo("ORD_001")).thenReturn(order);
                when(paymentMapper.findByOrderNo("ORD_001")).thenReturn(payment);
                when(paymentMapper.findByTradeNo(anyString())).thenReturn(null);
                when(distributedLock.tryLock("order:100")).thenReturn(true);
                when(orderMapper.findById(100L)).thenReturn(order);

                String result = paymentService.simulatePayNotify("ORD_001", "T_" + status);

                assertThat(result)
                        .as("Status %s should return success", status)
                        .isEqualTo("success");
                verify(paymentMapper, never().description("payment must not advance for status " + status))
                        .updateStatus(anyLong(), anyString(), anyString());
                verify(orderMapper, never().description("order must not advance for status " + status))
                        .updateStatus(anyLong(), anyString(), anyString());
                verify(inventoryService, never().description("stock must not deduct for status " + status))
                        .deductStock(anyLong(), anyInt());
                verify(distributedLock).unlock("order:100");
            }
        }
    }
}
