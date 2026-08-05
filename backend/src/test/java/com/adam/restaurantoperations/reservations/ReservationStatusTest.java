package com.adam.restaurantoperations.reservations;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationStatusTest {

    @Test
    void transitionModelSeparatesBlockingAndTerminalStatuses() {
        assertThat(ReservationStatus.PENDING.canTransitionTo(ReservationStatus.CONFIRMED)).isTrue();
        assertThat(ReservationStatus.PENDING.canTransitionTo(ReservationStatus.CANCELLED)).isTrue();
        assertThat(ReservationStatus.CONFIRMED.canTransitionTo(ReservationStatus.SEATED)).isTrue();
        assertThat(ReservationStatus.CONFIRMED.canTransitionTo(ReservationStatus.NO_SHOW)).isTrue();
        assertThat(ReservationStatus.SEATED.canTransitionTo(ReservationStatus.COMPLETED)).isTrue();
        assertThat(ReservationStatus.SEATED.canTransitionTo(ReservationStatus.CANCELLED)).isTrue();
        assertThat(ReservationStatus.COMPLETED.canTransitionTo(ReservationStatus.PENDING)).isFalse();
        assertThat(ReservationStatus.CONFIRMED.blocksAvailability()).isTrue();
        assertThat(ReservationStatus.SEATED.blocksAvailability()).isTrue();
        assertThat(ReservationStatus.CANCELLED.blocksAvailability()).isFalse();
        assertThat(ReservationStatus.NO_SHOW.isTerminal()).isTrue();
    }
}
