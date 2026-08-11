package com.adam.restaurantoperations.inventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.adam.restaurantoperations.audit.InventoryAuditService;
import com.adam.restaurantoperations.kitchen.KitchenTicketItemEntity;
import com.adam.restaurantoperations.menu.ModifierOptionRepository;
import com.adam.restaurantoperations.orders.OrderItemEntity;
import com.adam.restaurantoperations.orders.OrderItemModifierRepository;
import com.adam.restaurantoperations.users.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class StockConsumptionService {
    private final RecipeRepository recipes;
    private final RecipeIngredientRepository recipeIngredients;
    private final ModifierOptionIngredientRepository modifierIngredients;
    private final ModifierOptionRepository modifierOptions;
    private final OrderItemModifierRepository orderModifiers;
    private final StockMovementRepository movements;
    private final UserRepository users;
    private final InventoryAuditService audit;

    public StockConsumptionService(
            RecipeRepository recipes,
            RecipeIngredientRepository recipeIngredients,
            ModifierOptionIngredientRepository modifierIngredients,
            ModifierOptionRepository modifierOptions,
            OrderItemModifierRepository orderModifiers,
            StockMovementRepository movements,
            UserRepository users,
            InventoryAuditService audit) {
        this.recipes = recipes;
        this.recipeIngredients = recipeIngredients;
        this.modifierIngredients = modifierIngredients;
        this.modifierOptions = modifierOptions;
        this.orderModifiers = orderModifiers;
        this.movements = movements;
        this.users = users;
        this.audit = audit;
    }

    public void consumeForKitchenItem(
            KitchenTicketItemEntity kitchenItem,
            Long actorId,
            String ipAddress) {
        OrderItemEntity orderItem = kitchenItem.getOrderItem();
        Map<Long, Usage> usageByInventoryItem = new HashMap<>();
        recipes.findActiveByMenuItemIdForConsumption(orderItem.getMenuItem().getId())
                .ifPresent(recipe -> recipeIngredients.findByRecipeIdOrderByDisplayOrderAscIdAsc(recipe.getId())
                        .forEach(ingredient -> add(
                                usageByInventoryItem,
                                ingredient.getInventoryItem(),
                                ingredient.getQuantity(),
                                orderItem.getQuantity())));

        List<Long> optionIds = orderModifiers.findByOrderItemIdOrderByDisplayOrderAscIdAsc(orderItem.getId())
                .stream()
                .map(modifier -> modifier.getModifierOption().getId())
                .distinct()
                .sorted()
                .toList();
        for (Long optionId : optionIds) {
            modifierOptions.findByIdForOrderPricing(optionId)
                    .orElseThrow(() -> InventoryManagementException.conflict(
                            "Selected modifier option no longer exists"));
        }
        if (!optionIds.isEmpty()) {
            modifierIngredients
                    .findByModifierOptionIdInOrderByModifierOptionIdAscDisplayOrderAscIdAsc(optionIds)
                    .forEach(ingredient -> add(
                            usageByInventoryItem,
                            ingredient.getInventoryItem(),
                            ingredient.getQuantity(),
                            orderItem.getQuantity()));
        }

        Instant occurredAt = Instant.now();
        List<StockMovementEntity> created = new ArrayList<>();
        usageByInventoryItem.values().stream()
                .sorted(java.util.Comparator.comparing(usage -> usage.item().getId()))
                .forEach(usage -> created.add(new StockMovementEntity(
                        usage.item(),
                        StockMovementType.USAGE,
                        InventoryService.quantity(usage.quantity()),
                        occurredAt,
                        users.getReferenceById(actorId),
                        "KITCHEN_ITEM",
                        kitchenItem.getId(),
                        sourceKey(kitchenItem.getId(), usage.item().getId()),
                        null,
                        null,
                        null)));
        try {
            movements.saveAll(created);
            movements.flush();
        } catch (DataIntegrityViolationException exception) {
            throw InventoryManagementException.conflict(
                    "Kitchen-item stock usage was already recorded or conflicts with another request");
        }
        if (!created.isEmpty()) {
            audit.record(
                    "STOCK_USAGE_RECORDED",
                    actorId,
                    "KITCHEN_ITEM",
                    kitchenItem.getId(),
                    Map.of("movementCount", created.size(), "orderItemId", orderItem.getId()),
                    ipAddress);
        }
    }

    private void add(
            Map<Long, Usage> usage,
            InventoryItemEntity item,
            BigDecimal perUnitQuantity,
            int orderQuantity) {
        BigDecimal total = perUnitQuantity.multiply(BigDecimal.valueOf(orderQuantity));
        usage.merge(
                item.getId(),
                new Usage(item, total),
                (existing, additional) -> new Usage(item, existing.quantity().add(additional.quantity())));
    }

    private String sourceKey(Long kitchenItemId, Long inventoryItemId) {
        return "KITCHEN_ITEM:" + kitchenItemId + ":INVENTORY_ITEM:" + inventoryItemId;
    }

    private record Usage(InventoryItemEntity item, BigDecimal quantity) {
    }
}
