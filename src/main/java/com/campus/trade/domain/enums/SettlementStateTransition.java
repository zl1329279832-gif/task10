package com.campus.trade.domain.enums;

import java.util.EnumSet;
import java.util.Set;

public enum SettlementStateTransition {
    PENDING(SettlementStatus.SETTLED, SettlementStatus.FROZEN, SettlementStatus.FAILED),
    SETTLED(SettlementStatus.REVERSED),
    FAILED(SettlementStatus.PENDING),
    FROZEN(SettlementStatus.PENDING),
    REVERSED();

    private final Set<SettlementStatus> validTargets;

    SettlementStateTransition(SettlementStatus... targets) {
        this.validTargets = targets.length > 0 ? EnumSet.of(targets[0], targets) : EnumSet.noneOf(SettlementStatus.class);
    }

    public boolean canTransitTo(SettlementStatus target) { return validTargets.contains(target); }

    private static final java.util.Map<SettlementStatus, SettlementStateTransition> MAP = new java.util.EnumMap<>(SettlementStatus.class);
    static { for (SettlementStateTransition t : values()) MAP.put(SettlementStatus.valueOf(t.name()), t); }

    public static boolean isValid(SettlementStatus from, SettlementStatus to) {
        SettlementStateTransition t = MAP.get(from);
        return t != null && t.canTransitTo(to);
    }
}
