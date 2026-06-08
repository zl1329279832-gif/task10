package com.campus.trade.domain.enums;
import java.util.EnumSet;
import java.util.Set;
public enum OrderStateTransition {
    CREATED(OrderStatus.CANCELLED, OrderStatus.PAID),
    PAID(OrderStatus.SHIPPED, OrderStatus.REFUNDING, OrderStatus.DISPUTED),
    SHIPPED(OrderStatus.RECEIVED, OrderStatus.REFUNDING, OrderStatus.DISPUTED),
    RECEIVED(OrderStatus.SETTLED, OrderStatus.REFUNDING, OrderStatus.DISPUTED),
    REFUNDING(OrderStatus.REFUNDED, OrderStatus.DISPUTED, OrderStatus.PAID),
    DISPUTED(OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.RECEIVED, OrderStatus.REFUNDED, OrderStatus.CANCELLED),
    REFUNDED(), SETTLED(), CANCELLED(), CLOSED();
    private final Set<OrderStatus> validTargets;
    OrderStateTransition(OrderStatus... targets) {
        this.validTargets = targets.length > 0 ? EnumSet.of(targets[0], targets) : EnumSet.noneOf(OrderStatus.class);
    }
    public boolean canTransitTo(OrderStatus target) { return validTargets.contains(target); }
    private static final java.util.Map<OrderStatus, OrderStateTransition> MAP = new java.util.EnumMap<>(OrderStatus.class);
    static { for (OrderStateTransition t : values()) MAP.put(OrderStatus.valueOf(t.name()), t); }
    public static boolean isValid(OrderStatus from, OrderStatus to) {
        OrderStateTransition t = MAP.get(from);
        return t != null && t.canTransitTo(to);
    }
}
