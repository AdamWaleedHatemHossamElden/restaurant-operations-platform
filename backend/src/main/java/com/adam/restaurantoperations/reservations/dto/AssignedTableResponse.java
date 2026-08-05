package com.adam.restaurantoperations.reservations.dto;

import com.adam.restaurantoperations.tables.RestaurantTableEntity;

public record AssignedTableResponse(
        Long id,
        String tableNumber,
        String displayName,
        String section,
        int capacity) {

    public static AssignedTableResponse from(RestaurantTableEntity table) {
        if (table == null) {
            return null;
        }
        return new AssignedTableResponse(
                table.getId(),
                table.getTableNumber(),
                table.getDisplayName(),
                table.getSection(),
                table.getCapacity());
    }
}
