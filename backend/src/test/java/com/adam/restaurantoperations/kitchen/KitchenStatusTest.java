package com.adam.restaurantoperations.kitchen;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KitchenStatusTest {
    @Test
    void itemTransitionsAreStrictlyForwardAndReadyIsTerminal() {
        assertThat(KitchenItemStatus.QUEUED.canTransitionTo(KitchenItemStatus.PREPARING)).isTrue();
        assertThat(KitchenItemStatus.QUEUED.canTransitionTo(KitchenItemStatus.READY)).isFalse();
        assertThat(KitchenItemStatus.PREPARING.canTransitionTo(KitchenItemStatus.READY)).isTrue();
        assertThat(KitchenItemStatus.PREPARING.canTransitionTo(KitchenItemStatus.QUEUED)).isFalse();
        assertThat(KitchenItemStatus.READY.canTransitionTo(KitchenItemStatus.PREPARING)).isFalse();
    }
}
