package com.adam.restaurantoperations.kitchen;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public final class KitchenDtos {
    private KitchenDtos() {
    }

    public record KitchenItemStatusRequest(
            @NotNull KitchenItemStatus status,
            @NotNull @PositiveOrZero Long version) {
    }

    public record KitchenTableSummary(
            Long id,
            String tableNumber,
            String displayName,
            String section) {
    }

    public record KitchenReservationSummary(
            Long id,
            String reservationCode) {
    }

    public record KitchenModifierSnapshot(
            String groupName,
            String optionName) {
    }

    public record KitchenItemResponse(
            Long id,
            Long orderItemId,
            String itemCode,
            String itemName,
            int quantity,
            String notes,
            int displayOrder,
            KitchenItemStatus status,
            Instant startedAt,
            Instant readyAt,
            List<KitchenModifierSnapshot> modifiers) {
    }

    public record KitchenTicketResponse(
            Long id,
            KitchenTicketStatus status,
            long version,
            Long orderId,
            String orderNumber,
            KitchenTableSummary restaurantTable,
            KitchenReservationSummary reservation,
            Instant submittedAt,
            Instant createdAt,
            Instant startedAt,
            Instant readyAt,
            Instant cancelledAt,
            List<KitchenItemResponse> items) {
    }
}
