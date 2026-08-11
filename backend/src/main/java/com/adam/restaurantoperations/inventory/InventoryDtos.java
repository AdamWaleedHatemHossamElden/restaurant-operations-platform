package com.adam.restaurantoperations.inventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public final class InventoryDtos {
    private InventoryDtos() {
    }

    public record InventoryItemRequest(
            @NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 160) String name,
            @NotNull InventoryUnit unit,
            @NotNull @DecimalMin("0.000") @Digits(integer = 11, fraction = 3) BigDecimal reorderThreshold,
            @PositiveOrZero Long version) {
    }

    public record ActivationRequest(
            @NotNull Boolean value,
            @NotNull @PositiveOrZero Long version) {
    }

    public record InventoryItemResponse(
            Long id,
            String code,
            String name,
            InventoryUnit unit,
            BigDecimal reorderThreshold,
            BigDecimal onHand,
            boolean lowStock,
            boolean active,
            Instant createdAt,
            Instant updatedAt,
            long version) {
    }

    public record ManualMovementRequest(
            @NotNull Long inventoryItemId,
            @NotNull StockMovementType movementType,
            @NotNull @DecimalMin(value = "0.001") @Digits(integer = 11, fraction = 3) BigDecimal quantity,
            @Size(max = 500) String reason) {
    }

    public record StockMovementResponse(
            Long id,
            Long inventoryItemId,
            String inventoryCode,
            String inventoryName,
            InventoryUnit unit,
            StockMovementType movementType,
            BigDecimal quantity,
            BigDecimal signedQuantity,
            Instant occurredAt,
            String referenceType,
            Long referenceId,
            String reason,
            BigDecimal unitCost,
            BigDecimal totalCost) {
    }

    public record IngredientInput(
            @NotNull Long inventoryItemId,
            @NotNull @DecimalMin(value = "0.001") @Digits(integer = 11, fraction = 3) BigDecimal quantity,
            @NotNull @PositiveOrZero Integer displayOrder) {
    }

    public record RecipeStateRequest(
            @NotNull Boolean active,
            @PositiveOrZero Long version) {
    }

    public record RecipeIngredientsRequest(
            @NotNull @PositiveOrZero Long version,
            @NotNull List<@Valid IngredientInput> ingredients) {
    }

    public record ModifierIngredientsRequest(
            @NotNull @PositiveOrZero Long optionVersion,
            @NotNull List<@Valid IngredientInput> ingredients) {
    }

    public record IngredientResponse(
            Long inventoryItemId,
            String inventoryCode,
            String inventoryName,
            InventoryUnit unit,
            BigDecimal quantity,
            int displayOrder) {
    }

    public record RecipeResponse(
            Long id,
            Long menuItemId,
            String menuItemCode,
            String menuItemName,
            boolean active,
            long version,
            List<IngredientResponse> ingredients) {
    }

    public record ModifierIngredientsResponse(
            Long modifierOptionId,
            String modifierOptionName,
            long optionVersion,
            List<IngredientResponse> ingredients) {
    }

    public record SupplierRequest(
            @NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 160) String name,
            @Size(max = 160) String contactName,
            @Email @Size(max = 254) String email,
            @Size(max = 40) String phone,
            @Size(max = 1000) String notes,
            @PositiveOrZero Long version) {
    }

    public record SupplierResponse(
            Long id,
            String code,
            String name,
            String contactName,
            String email,
            String phone,
            String notes,
            boolean active,
            Instant createdAt,
            Instant updatedAt,
            long version,
            List<SupplierItemResponse> inventoryItems) {
    }

    public record SupplierItemRequest(
            @NotNull Long inventoryItemId,
            @Size(max = 80) String supplierItemCode,
            @NotNull @DecimalMin("0.0000") @Digits(integer = 8, fraction = 4) BigDecimal unitCost,
            @NotNull Boolean active,
            @PositiveOrZero Long version) {
    }

    public record SupplierItemResponse(
            Long id,
            Long inventoryItemId,
            String inventoryCode,
            String inventoryName,
            InventoryUnit unit,
            String supplierItemCode,
            BigDecimal unitCost,
            boolean active,
            long version) {
    }

    public record PurchaseOrderRequest(
            @NotNull Long supplierId,
            @Size(max = 1000) String notes,
            @PositiveOrZero Long version) {
    }

    public record PurchaseOrderLineRequest(
            @NotNull Long inventoryItemId,
            @NotNull @DecimalMin("0.001") @Digits(integer = 11, fraction = 3) BigDecimal quantity,
            @NotNull @PositiveOrZero Long version) {
    }

    public record PurchaseOrderLineUpdateRequest(
            @NotNull @DecimalMin("0.001") @Digits(integer = 11, fraction = 3) BigDecimal quantity,
            @NotNull @PositiveOrZero Long version) {
    }

    public record PurchaseOrderStatusRequest(
            @NotNull PurchaseOrderStatus status,
            @NotNull @PositiveOrZero Long version) {
    }

    public record PurchaseReceiptRequest(
            @NotNull Long purchaseOrderItemId,
            @NotNull @DecimalMin("0.001") @Digits(integer = 11, fraction = 3) BigDecimal quantity,
            @NotNull @PositiveOrZero Long version) {
    }

    public record PurchaseOrderLineResponse(
            Long id,
            Long inventoryItemId,
            String inventoryCode,
            String inventoryName,
            InventoryUnit unit,
            BigDecimal orderedQuantity,
            BigDecimal receivedQuantity,
            BigDecimal remainingQuantity,
            BigDecimal unitCost,
            BigDecimal lineTotal,
            int displayOrder) {
    }

    public record PurchaseOrderResponse(
            Long id,
            String purchaseOrderNumber,
            Long supplierId,
            String supplierCode,
            String supplierName,
            PurchaseOrderStatus status,
            String notes,
            BigDecimal subtotal,
            BigDecimal total,
            Instant orderedAt,
            Instant receivedAt,
            Instant cancelledAt,
            Instant createdAt,
            Instant updatedAt,
            long version,
            List<PurchaseOrderLineResponse> items) {
    }
}
