package com.adam.restaurantoperations.common.health;

import org.springframework.stereotype.Service;

@Service
public class HealthService {

    private static final String SERVICE_NAME = "restaurant-operations-backend";

    public HealthResponse currentStatus() {
        return new HealthResponse("UP", SERVICE_NAME);
    }
}
