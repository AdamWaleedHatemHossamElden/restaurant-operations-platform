package com.adam.restaurantoperations.common.health;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HealthServiceTest {

    private final HealthService healthService = new HealthService();

    @Test
    void reportsTheServiceAsUp() {
        assertThat(healthService.currentStatus())
                .isEqualTo(new HealthResponse("UP", "restaurant-operations-backend"));
    }
}
