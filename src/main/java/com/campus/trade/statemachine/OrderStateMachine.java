package com.campus.trade.statemachine;

import com.campus.trade.common.BusinessException;
import com.campus.trade.common.ResultCode;
import com.campus.trade.model.enums.OrderStatus;

import java.util.EnumMap;
import java.util.Map;

public class OrderStateMachine {

    private static final Map<OrderStatus, Map<OrderEvent, OrderStatus>> TRANSITIONS = new EnumMap<>(OrderStatus.class);

    static {
        // PENDING_PAYMENT transitions
        addTransition(OrderStatus.PENDING_PAYMENT, OrderEvent.PAY, OrderStatus.PAID);
        addTransition(OrderStatus.PENDING_PAYMENT, OrderEvent.PAY_TIMEOUT, OrderStatus.CLOSED);
        addTransition(OrderStatus.PENDING_PAYMENT, OrderEvent.CLOSE, OrderStatus.CLOSED);

        // PAID transitions
        addTransition(OrderStatus.PAID, OrderEvent.SHIP, OrderStatus.SHIPPED);
        addTransition(OrderStatus.PAID, OrderEvent.REQUEST_REFUND, OrderStatus.REFUND_REQUESTED);
        addTransition(OrderStatus.PAID, OrderEvent.CLOSE, OrderStatus.CLOSED);

        // SHIPPED transitions
        addTransition(OrderStatus.SHIPPED, OrderEvent.CONFIRM_RECEIVE, OrderStatus.RECEIVED);
        addTransition(OrderStatus.SHIPPED, OrderEvent.REQUEST_REFUND, OrderStatus.REFUND_REQUESTED);

        // RECEIVED transitions
        addTransition(OrderStatus.RECEIVED, OrderEvent.COMPLETE, OrderStatus.COMPLETED);
        addTransition(OrderStatus.RECEIVED, OrderEvent.REQUEST_REFUND, OrderStatus.REFUND_REQUESTED);

        // REFUND_REQUESTED transitions
        addTransition(OrderStatus.REFUND_REQUESTED, OrderEvent.APPROVE_REFUND, OrderStatus.REFUND_APPROVED);
        addTransition(OrderStatus.REFUND_REQUESTED, OrderEvent.REJECT_REFUND, OrderStatus.REFUND_REJECTED);

        // REFUND_REJECTED transitions
        addTransition(OrderStatus.REFUND_REJECTED, OrderEvent.OPEN_DISPUTE, OrderStatus.DISPUTE);
        addTransition(OrderStatus.REFUND_REJECTED, OrderEvent.COMPLETE, OrderStatus.COMPLETED);

        // DISPUTE transitions
        addTransition(OrderStatus.DISPUTE, OrderEvent.ARBITRATE, OrderStatus.ARBITRATED);
    }

    private static void addTransition(OrderStatus from, OrderEvent event, OrderStatus to) {
        TRANSITIONS.computeIfAbsent(from, k -> new EnumMap<>(OrderEvent.class)).put(event, to);
    }

    public static OrderStatus transition(OrderStatus currentStatus, OrderEvent event) {
        Map<OrderEvent, OrderStatus> eventMap = TRANSITIONS.get(currentStatus);
        if (eventMap == null || !eventMap.containsKey(event)) {
            throw new BusinessException(ResultCode.ORDER_STATUS_INVALID,
                    String.format("非法状态转换: %s + %s", currentStatus, event));
        }
        return eventMap.get(event);
    }

    public static boolean canTransition(OrderStatus currentStatus, OrderEvent event) {
        Map<OrderEvent, OrderStatus> eventMap = TRANSITIONS.get(currentStatus);
        return eventMap != null && eventMap.containsKey(event);
    }
}
