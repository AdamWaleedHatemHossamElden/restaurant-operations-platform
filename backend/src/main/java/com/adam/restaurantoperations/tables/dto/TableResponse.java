package com.adam.restaurantoperations.tables.dto;

import java.time.Instant;

import com.adam.restaurantoperations.tables.RestaurantTableEntity;
import com.adam.restaurantoperations.tables.TableStatus;

public record TableResponse(
        Long id,
        String tableNumber,
        String displayName,
        int capacity,
        String section,
        TableStatus status,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    public static TableResponse from(RestaurantTableEntity table) {
        return new TableResponse(
                table.getId(),
                table.getTableNumber(),
                table.getDisplayName(),
                table.getCapacity(),
                table.getSection(),
                table.getStatus(),
                table.isActive(),
                table.getCreatedAt(),
                table.getUpdatedAt(),
                table.getVersion());
    }
}
