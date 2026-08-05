package com.adam.restaurantoperations.reservations.dto;

import com.adam.restaurantoperations.tables.RestaurantTableEntity;

public record TableAvailabilityResponse(
        Long id,
        String tableNumber,
        String displayName,
        String section,
        int capacity) {

    public static TableAvailabilityResponse from(RestaurantTableEntity table) {
        return new TableAvailabilityResponse(
                table.getId(),
                table.getTableNumber(),
                table.getDisplayName(),
                table.getSection(),
                table.getCapacity());
    }
}
