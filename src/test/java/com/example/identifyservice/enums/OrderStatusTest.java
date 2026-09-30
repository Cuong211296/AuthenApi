package com.example.identifyservice.enums;

import org.junit.jupiter.api.Test;

import static com.example.identifyservice.enums.OrderStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusTest {
    @Test
    void allowedTransitions() {
        assertThat(PENDING_CONFIRM.canTransitionTo(CONFIRMED)).isTrue();
        assertThat(CONFIRMED.canTransitionTo(SHIPPING)).isTrue();
        assertThat(SHIPPING.canTransitionTo(COMPLETED)).isTrue();
        assertThat(PENDING_CONFIRM.canTransitionTo(CANCELLED)).isTrue();
        assertThat(PENDING_PAYMENT.canTransitionTo(CANCELLED)).isTrue();
    }

    @Test
    void forbiddenTransitions() {
        assertThat(PENDING_CONFIRM.canTransitionTo(COMPLETED)).isFalse();
        assertThat(PENDING_PAYMENT.canTransitionTo(CONFIRMED)).isFalse();
        assertThat(SHIPPING.canTransitionTo(CANCELLED)).isFalse();
        assertThat(COMPLETED.canTransitionTo(CANCELLED)).isFalse();
        assertThat(CANCELLED.canTransitionTo(CONFIRMED)).isFalse();
    }
}
