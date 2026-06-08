package com.campus.trade.statemachine;

import com.campus.trade.common.BusinessException;
import com.campus.trade.model.enums.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class OrderStateMachineTest {

    @ParameterizedTest
    @DisplayName("合法状态转换")
    @CsvSource({
            "PENDING_PAYMENT, PAY, PAID",
            "PENDING_PAYMENT, PAY_TIMEOUT, CLOSED",
            "PENDING_PAYMENT, CLOSE, CLOSED",
            "PAID, SHIP, SHIPPED",
            "PAID, REQUEST_REFUND, REFUND_REQUESTED",
            "PAID, CLOSE, CLOSED",
            "SHIPPED, CONFIRM_RECEIVE, RECEIVED",
            "SHIPPED, REQUEST_REFUND, REFUND_REQUESTED",
            "RECEIVED, COMPLETE, COMPLETED",
            "RECEIVED, REQUEST_REFUND, REFUND_REQUESTED",
            "REFUND_REQUESTED, APPROVE_REFUND, REFUND_APPROVED",
            "REFUND_REQUESTED, REJECT_REFUND, REFUND_REJECTED",
            "REFUND_REJECTED, OPEN_DISPUTE, DISPUTE",
            "REFUND_REJECTED, COMPLETE, COMPLETED",
            "DISPUTE, ARBITRATE, ARBITRATED"
    })
    void testValidTransitions(String fromStr, String eventStr, String expectedStr) {
        OrderStatus from = OrderStatus.valueOf(fromStr);
        OrderEvent event = OrderEvent.valueOf(eventStr);
        OrderStatus expected = OrderStatus.valueOf(expectedStr);

        OrderStatus result = OrderStateMachine.transition(from, event);
        assertEquals(expected, result);
    }

    @ParameterizedTest
    @DisplayName("非法状态转换应抛异常")
    @CsvSource({
            "COMPLETED, PAY",
            "CLOSED, PAY",
            "CLOSED, SHIP",
            "PENDING_PAYMENT, SHIP",
            "PENDING_PAYMENT, CONFIRM_RECEIVE",
            "PAID, CONFIRM_RECEIVE",
            "PAID, PAY",
            "SHIPPED, SHIP",
            "RECEIVED, SHIP",
            "COMPLETED, REQUEST_REFUND",
            "REFUND_APPROVED, PAY",
            "ARBITRATED, PAY",
            "ARBITRATED, ARBITRATE"
    })
    void testInvalidTransitions(String fromStr, String eventStr) {
        OrderStatus from = OrderStatus.valueOf(fromStr);
        OrderEvent event = OrderEvent.valueOf(eventStr);

        assertThrows(BusinessException.class, () -> OrderStateMachine.transition(from, event));
    }

    @Test
    @DisplayName("canTransition 返回正确结果")
    void testCanTransition() {
        assertTrue(OrderStateMachine.canTransition(OrderStatus.PENDING_PAYMENT, OrderEvent.PAY));
        assertFalse(OrderStateMachine.canTransition(OrderStatus.COMPLETED, OrderEvent.PAY));
        assertFalse(OrderStateMachine.canTransition(OrderStatus.CLOSED, OrderEvent.SHIP));
    }

    @Test
    @DisplayName("完整订单生命周期: 下单→支付→发货→收货→完成")
    void testFullLifecycle() {
        OrderStatus status = OrderStatus.PENDING_PAYMENT;
        status = OrderStateMachine.transition(status, OrderEvent.PAY);
        assertEquals(OrderStatus.PAID, status);

        status = OrderStateMachine.transition(status, OrderEvent.SHIP);
        assertEquals(OrderStatus.SHIPPED, status);

        status = OrderStateMachine.transition(status, OrderEvent.CONFIRM_RECEIVE);
        assertEquals(OrderStatus.RECEIVED, status);

        status = OrderStateMachine.transition(status, OrderEvent.COMPLETE);
        assertEquals(OrderStatus.COMPLETED, status);
    }

    @Test
    @DisplayName("退款被拒后发起争议再仲裁")
    void testRefundDisputeArbitration() {
        OrderStatus status = OrderStatus.PAID;
        status = OrderStateMachine.transition(status, OrderEvent.REQUEST_REFUND);
        assertEquals(OrderStatus.REFUND_REQUESTED, status);

        status = OrderStateMachine.transition(status, OrderEvent.REJECT_REFUND);
        assertEquals(OrderStatus.REFUND_REJECTED, status);

        status = OrderStateMachine.transition(status, OrderEvent.OPEN_DISPUTE);
        assertEquals(OrderStatus.DISPUTE, status);

        status = OrderStateMachine.transition(status, OrderEvent.ARBITRATE);
        assertEquals(OrderStatus.ARBITRATED, status);
    }

    @Test
    @DisplayName("支付超时关闭订单")
    void testPayTimeout() {
        OrderStatus status = OrderStatus.PENDING_PAYMENT;
        status = OrderStateMachine.transition(status, OrderEvent.PAY_TIMEOUT);
        assertEquals(OrderStatus.CLOSED, status);
    }
}
