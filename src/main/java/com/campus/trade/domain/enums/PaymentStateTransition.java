package com.campus.trade.domain.enums;

import java.util.EnumSet;
import java.util.Set;

public enum PaymentStateTransition {
    PENDING(PaymentStatus.SUCCESS, PaymentStatus.CLOSED),
    SUCCESS(PaymentStatus.FROZEN),
    FROZEN(PaymentStatus.SUCCESS, PaymentStatus.CLOSED),
    CLOSED();

    private final Set<PaymentStatus> validTargets;

    PaymentStateTransition(PaymentStatus... targets) {
        this.validTargets = targets.length > 0 ? EnumSet.of(targets[0], targets) : EnumSet.noneOf(PaymentStatus.class);
    }

    public boolean canTransitTo(PaymentStatus target) { return validTargets.contains(target); }

    private static final java.util.Map<PaymentStatus, PaymentStateTransition> MAP = new java.util.EnumMap<>(PaymentStatus.class);
    static { for (PaymentStateTransition t : values()) MAP.put(PaymentStatus.valueOf(t.name()), t); }

    public static boolean isValid(PaymentStatus from, PaymentStatus to) {
        PaymentStateTransition t = MAP.get(from);
        return t != null && t.canTransitTo(to);
    }
}
