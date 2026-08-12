package com.adam.restaurantoperations.staff;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShiftStatusTest {
    @Test
    void onlyScheduledIsNonTerminal() {
        assertThat(ShiftStatus.SCHEDULED.isTerminal()).isFalse();
        assertThat(ShiftStatus.COMPLETED.isTerminal()).isTrue();
        assertThat(ShiftStatus.CANCELLED.isTerminal()).isTrue();
    }
}
