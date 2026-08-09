package com.adam.restaurantoperations.orders;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public final class OrderDtos {
    private OrderDtos() {
    }

    public record CreateOrderRequest(
            @NotNull @Positive Long restaurantTableId,
            @Positive Long reservationId,
            @Size(max = 2000) String notes) {
    }

    public record UpdateOrderRequest(
            @NotNull @Positive Long restaurantTableId,
            @Positive Long reservationId,
            @Size(max = 2000) String notes,
            @NotNull @PositiveOrZero Long version) {
    }

    public record ModifierSelection(
            @NotNull @Positive Long modifierGroupId,
            @NotNull @Size(max = 20) List<@NotNull @Positive Long> optionIds) {
    }

    public record AddOrderItemRequest(
            @NotNull @Positive Long menuItemId,
            @Min(1) @Max(99) int quantity,
            @Size(max = 1000) String notes,
            @NotNull List<@Valid ModifierSelection> modifierSelections,
            @NotNull @PositiveOrZero Long version) {
    }

    public record UpdateOrderItemRequest(
            @Min(1) @Max(99) int quantity,
            @Size(max = 1000) String notes,
            List<@Valid ModifierSelection> modifierSelections,
            @NotNull @PositiveOrZero Long version) {
    }

    public record OrderStatusRequest(
            @NotNull OrderStatus status,
            @NotNull @PositiveOrZero Long version) {
    }

    public record TableSummary(
            Long id,
            String tableNumber,
            String displayName,
            String section) {
    }

    public record ReservationSummary(
            Long id,
            String reservationCode,
            String guestName) {
    }

    public record ModifierSnapshotResponse(
            Long id,
            Long modifierGroupId,
            Long modifierOptionId,
            String groupName,
            String optionName,
            String priceAdjustment,
            int displayOrder) {
    }

    public record OrderItemResponse(
            Long id,
            Long menuItemId,
            String itemCode,
            String itemName,
            String basePrice,
            int quantity,
            String notes,
            String unitTotal,
            String lineTotal,
            int displayOrder,
            List<ModifierSnapshotResponse> modifiers,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record StatusHistoryResponse(
            Long id,
            OrderStatus fromStatus,
            OrderStatus toStatus,
            Instant changedAt,
            Long changedByUserId) {
    }

    public record OrderResponse(
            Long id,
            String orderNumber,
            OrderStatus status,
            long version,
            TableSummary restaurantTable,
            ReservationSummary reservation,
            String notes,
            String subtotal,
            String total,
            int itemCount,
            Instant createdAt,
            Instant updatedAt,
            Instant submittedAt,
            Instant completedAt,
            Instant cancelledAt,
            List<OrderItemResponse> items,
            List<StatusHistoryResponse> history) {
    }
}
