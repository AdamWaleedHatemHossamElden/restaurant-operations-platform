package com.adam.restaurantoperations.orders;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusTest {
    @Test
    void transitionModelFreezesSubmittedAndTerminalOrders() {
        assertThat(OrderStatus.OPEN.canTransitionTo(OrderStatus.SUBMITTED)).isTrue();
        assertThat(OrderStatus.OPEN.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
        assertThat(OrderStatus.SUBMITTED.canTransitionTo(OrderStatus.COMPLETED)).isTrue();
        assertThat(OrderStatus.SUBMITTED.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
        assertThat(OrderStatus.SUBMITTED.canTransitionTo(OrderStatus.OPEN)).isFalse();
        assertThat(OrderStatus.COMPLETED.canTransitionTo(OrderStatus.OPEN)).isFalse();
        assertThat(OrderStatus.CANCELLED.canTransitionTo(OrderStatus.OPEN)).isFalse();
        assertThat(OrderStatus.OPEN.isMutable()).isTrue();
        assertThat(OrderStatus.SUBMITTED.isMutable()).isFalse();
    }
}
