package com.campus.trade.service;

import com.campus.trade.domain.enums.SettlementStateTransition;
import com.campus.trade.domain.enums.SettlementStatus;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

@DisplayName("SettlementStateTransition")
class SettlementStateTransitionTest {

    @Nested @DisplayName("Valid transitions") class Valid {
        @Test @DisplayName("PENDING -> SETTLED") void pendingToSettled() {
            assertThat(SettlementStateTransition.isValid(SettlementStatus.PENDING, SettlementStatus.SETTLED)).isTrue();
        }
        @Test @DisplayName("PENDING -> FROZEN") void pendingToFrozen() {
            assertThat(SettlementStateTransition.isValid(SettlementStatus.PENDING, SettlementStatus.FROZEN)).isTrue();
        }
        @Test @DisplayName("PENDING -> FAILED") void pendingToFailed() {
            assertThat(SettlementStateTransition.isValid(SettlementStatus.PENDING, SettlementStatus.FAILED)).isTrue();
        }
        @Test @DisplayName("FROZEN -> PENDING (unfreeze)") void frozenToPending() {
            assertThat(SettlementStateTransition.isValid(SettlementStatus.FROZEN, SettlementStatus.PENDING)).isTrue();
        }
        @Test @DisplayName("FAILED -> PENDING (retry)") void failedToPending() {
            assertThat(SettlementStateTransition.isValid(SettlementStatus.FAILED, SettlementStatus.PENDING)).isTrue();
        }
    }

    @Nested @DisplayName("Invalid transitions") class Invalid {
        @Test @DisplayName("SETTLED is terminal") void settledTerminal() {
            assertThat(SettlementStateTransition.isValid(SettlementStatus.SETTLED, SettlementStatus.PENDING)).isFalse();
            assertThat(SettlementStateTransition.isValid(SettlementStatus.SETTLED, SettlementStatus.FROZEN)).isFalse();
        }
        @Test @DisplayName("FROZEN -> SETTLED (must unfreeze first)") void frozenToSettled() {
            assertThat(SettlementStateTransition.isValid(SettlementStatus.FROZEN, SettlementStatus.SETTLED)).isFalse();
        }
        @Test @DisplayName("FAILED -> SETTLED (must retry first)") void failedToSettled() {
            assertThat(SettlementStateTransition.isValid(SettlementStatus.FAILED, SettlementStatus.SETTLED)).isFalse();
        }
        @Test @DisplayName("PENDING -> PENDING (self-loop)") void pendingSelf() {
            assertThat(SettlementStateTransition.isValid(SettlementStatus.PENDING, SettlementStatus.PENDING)).isFalse();
        }
    }
}
