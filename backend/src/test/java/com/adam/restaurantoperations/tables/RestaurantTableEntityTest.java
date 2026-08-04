package com.adam.restaurantoperations.tables;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RestaurantTableEntityTest {

    @Test
    void startsActiveAndSupportsOperationalUpdatesWithoutDeletion() {
        var table = new RestaurantTableEntity("T-01", "Window", 4, "Main", TableStatus.AVAILABLE);

        assertThat(table.isActive()).isTrue();

        table.update("T-02", "Patio", 6, "Outside", TableStatus.OUT_OF_SERVICE);
        table.setActive(false);

        assertThat(table.getTableNumber()).isEqualTo("T-02");
        assertThat(table.getDisplayName()).isEqualTo("Patio");
        assertThat(table.getCapacity()).isEqualTo(6);
        assertThat(table.getSection()).isEqualTo("Outside");
        assertThat(table.getStatus()).isEqualTo(TableStatus.OUT_OF_SERVICE);
        assertThat(table.isActive()).isFalse();
    }
}
