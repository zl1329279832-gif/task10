package com.campus.trade.service;

import com.campus.trade.domain.enums.PaymentStateTransition;
import com.campus.trade.domain.enums.PaymentStatus;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

@DisplayName("PaymentStateTransition")
class PaymentStateTransitionTest {

    @Nested @DisplayName("Valid transitions") class Valid {
        @Test @DisplayName("PENDING -> SUCCESS") void pendingToSuccess() {
            assertThat(PaymentStateTransition.isValid(PaymentStatus.PENDING, PaymentStatus.SUCCESS)).isTrue();
        }
        @Test @DisplayName("PENDING -> CLOSED") void pendingToClosed() {
            assertThat(PaymentStateTransition.isValid(PaymentStatus.PENDING, PaymentStatus.CLOSED)).isTrue();
        }
        @Test @DisplayName("SUCCESS -> FROZEN") void successToFrozen() {
            assertThat(PaymentStateTransition.isValid(PaymentStatus.SUCCESS, PaymentStatus.FROZEN)).isTrue();
        }
        @Test @DisplayName("FROZEN -> SUCCESS (unfreeze)") void frozenToSuccess() {
            assertThat(PaymentStateTransition.isValid(PaymentStatus.FROZEN, PaymentStatus.SUCCESS)).isTrue();
        }
        @Test @DisplayName("FROZEN -> CLOSED (full refund)") void frozenToClosed() {
            assertThat(PaymentStateTransition.isValid(PaymentStatus.FROZEN, PaymentStatus.CLOSED)).isTrue();
        }
    }

    @Nested @DisplayName("Invalid transitions") class Invalid {
        @Test @DisplayName("PENDING -> FROZEN (must go through SUCCESS first)") void pendingToFrozen() {
            assertThat(PaymentStateTransition.isValid(PaymentStatus.PENDING, PaymentStatus.FROZEN)).isFalse();
        }
        @Test @DisplayName("FROZEN -> PENDING") void frozenToPending() {
            assertThat(PaymentStateTransition.isValid(PaymentStatus.FROZEN, PaymentStatus.PENDING)).isFalse();
        }
        @Test @DisplayName("CLOSED is terminal") void closedToAnything() {
            assertThat(PaymentStateTransition.isValid(PaymentStatus.CLOSED, PaymentStatus.SUCCESS)).isFalse();
            assertThat(PaymentStateTransition.isValid(PaymentStatus.CLOSED, PaymentStatus.FROZEN)).isFalse();
            assertThat(PaymentStateTransition.isValid(PaymentStatus.CLOSED, PaymentStatus.PENDING)).isFalse();
        }
        @Test @DisplayName("SUCCESS -> PENDING") void successToPending() {
            assertThat(PaymentStateTransition.isValid(PaymentStatus.SUCCESS, PaymentStatus.PENDING)).isFalse();
        }
        @Test @DisplayName("SUCCESS -> CLOSED (must freeze first)") void successToClosed() {
            assertThat(PaymentStateTransition.isValid(PaymentStatus.SUCCESS, PaymentStatus.CLOSED)).isFalse();
        }
    }
}
