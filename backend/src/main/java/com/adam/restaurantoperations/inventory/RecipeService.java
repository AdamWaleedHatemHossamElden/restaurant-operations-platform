package com.adam.restaurantoperations.inventory;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.adam.restaurantoperations.audit.InventoryAuditService;
import com.adam.restaurantoperations.inventory.InventoryDtos.IngredientInput;
import com.adam.restaurantoperations.inventory.InventoryDtos.IngredientResponse;
import com.adam.restaurantoperations.inventory.InventoryDtos.ModifierIngredientsRequest;
import com.adam.restaurantoperations.inventory.InventoryDtos.ModifierIngredientsResponse;
import com.adam.restaurantoperations.inventory.InventoryDtos.RecipeIngredientsRequest;
import com.adam.restaurantoperations.inventory.InventoryDtos.RecipeResponse;
import com.adam.restaurantoperations.inventory.InventoryDtos.RecipeStateRequest;
import com.adam.restaurantoperations.menu.MenuItemEntity;
import com.adam.restaurantoperations.menu.MenuItemRepository;
import com.adam.restaurantoperations.menu.ModifierOptionEntity;
import com.adam.restaurantoperations.menu.ModifierOptionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecipeService {
    private final RecipeRepository recipes;
    private final RecipeIngredientRepository ingredients;
    private final ModifierOptionIngredientRepository modifierIngredients;
    private final InventoryItemRepository inventoryItems;
    private final MenuItemRepository menuItems;
    private final ModifierOptionRepository modifierOptions;
    private final InventoryAuditService audit;

    public RecipeService(
            RecipeRepository recipes,
            RecipeIngredientRepository ingredients,
            ModifierOptionIngredientRepository modifierIngredients,
            InventoryItemRepository inventoryItems,
            MenuItemRepository menuItems,
            ModifierOptionRepository modifierOptions,
            InventoryAuditService audit) {
        this.recipes = recipes;
        this.ingredients = ingredients;
        this.modifierIngredients = modifierIngredients;
        this.inventoryItems = inventoryItems;
        this.menuItems = menuItems;
        this.modifierOptions = modifierOptions;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<RecipeResponse> list() {
        return recipes.findAllByOrderByMenuItemNameAscIdAsc().stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public RecipeResponse getByMenuItem(Long menuItemId) {
        return response(recipes.findByMenuItemId(menuItemId)
                .orElseThrow(() -> InventoryManagementException.notFound("Recipe")));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RecipeResponse setRecipeState(
            Long menuItemId,
            RecipeStateRequest request,
            Long actorId,
            String ipAddress) {
        RecipeEntity recipe = recipes.findByMenuItemIdForUpdate(menuItemId).orElse(null);
        if (recipe == null) {
            if (request.version() != null) {
                throw InventoryManagementException.stale();
            }
            MenuItemEntity menuItem = menuItems.findById(menuItemId)
                    .orElseThrow(() -> InventoryManagementException.notFound("Menu item"));
            recipe = new RecipeEntity(menuItem, request.active());
        } else {
            verifyVersion(recipe, request.version());
            recipe.setActive(request.active());
        }
        RecipeEntity saved = save(recipe);
        audit.record(
                "RECIPE_UPDATED",
                actorId,
                "RECIPE",
                saved.getId(),
                Map.of("menuItemId", menuItemId, "active", request.active()),
                ipAddress);
        return response(saved);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RecipeResponse replaceIngredients(
            Long menuItemId,
            RecipeIngredientsRequest request,
            Long actorId,
            String ipAddress) {
        RecipeEntity recipe = recipes.findByMenuItemIdForUpdate(menuItemId)
                .orElseThrow(() -> InventoryManagementException.notFound("Recipe"));
        verifyVersion(recipe, request.version());
        validateShape(request.ingredients());
        List<RecipeIngredientEntity> replacements = request.ingredients().stream()
                .map(input -> new RecipeIngredientEntity(
                        recipe,
                        activeInventoryItem(input.inventoryItemId()),
                        InventoryService.quantity(input.quantity()),
                        input.displayOrder()))
                .toList();
        ingredients.deleteByRecipeId(recipe.getId());
        ingredients.flush();
        ingredients.saveAll(replacements);
        ingredients.flush();
        recipe.touch();
        RecipeEntity saved = save(recipe);
        audit.record(
                "RECIPE_UPDATED",
                actorId,
                "RECIPE",
                recipe.getId(),
                Map.of("menuItemId", menuItemId, "ingredientCount", replacements.size()),
                ipAddress);
        return response(saved);
    }

    @Transactional(readOnly = true)
    public ModifierIngredientsResponse getModifierIngredients(Long optionId) {
        ModifierOptionEntity option = modifierOptions.findById(optionId)
                .orElseThrow(() -> InventoryManagementException.notFound("Modifier option"));
        return modifierResponse(option);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ModifierIngredientsResponse replaceModifierIngredients(
            Long optionId,
            ModifierIngredientsRequest request,
            Long actorId,
            String ipAddress) {
        ModifierOptionEntity option = modifierOptions.findByIdForInventoryUpdate(optionId)
                .orElseThrow(() -> InventoryManagementException.notFound("Modifier option"));
        if (option.getVersion() != request.optionVersion()) {
            throw InventoryManagementException.stale();
        }
        validateShape(request.ingredients());
        List<ModifierOptionIngredientEntity> replacements = request.ingredients().stream()
                .map(input -> new ModifierOptionIngredientEntity(
                        option,
                        activeInventoryItem(input.inventoryItemId()),
                        InventoryService.quantity(input.quantity()),
                        input.displayOrder()))
                .toList();
        modifierIngredients.deleteByModifierOptionId(optionId);
        modifierIngredients.flush();
        modifierIngredients.saveAll(replacements);
        modifierIngredients.flush();
        option.touch();
        modifierOptions.saveAndFlush(option);
        audit.record(
                "MODIFIER_INGREDIENTS_UPDATED",
                actorId,
                "MODIFIER_OPTION",
                optionId,
                Map.of("ingredientCount", replacements.size()),
                ipAddress);
        return modifierResponse(option);
    }

    private void validateShape(List<IngredientInput> requested) {
        Set<Long> itemIds = new HashSet<>();
        Set<Integer> displayOrders = new HashSet<>();
        for (IngredientInput ingredient : requested) {
            if (!itemIds.add(ingredient.inventoryItemId())) {
                throw InventoryManagementException.badRequest(
                        "An inventory item may appear only once in an ingredient list");
            }
            if (!displayOrders.add(ingredient.displayOrder())) {
                throw InventoryManagementException.badRequest("Ingredient display orders must be unique");
            }
        }
    }

    private InventoryItemEntity activeInventoryItem(Long id) {
        InventoryItemEntity item = inventoryItems.findByIdForConfiguration(id)
                .orElseThrow(() -> InventoryManagementException.notFound("Inventory item"));
        if (!item.isActive()) {
            throw InventoryManagementException.conflict("Inactive inventory items cannot be assigned");
        }
        return item;
    }

    private RecipeResponse response(RecipeEntity recipe) {
        return new RecipeResponse(
                recipe.getId(),
                recipe.getMenuItem().getId(),
                recipe.getMenuItem().getCode(),
                recipe.getMenuItem().getName(),
                recipe.isActive(),
                recipe.getVersion(),
                ingredients.findByRecipeIdOrderByDisplayOrderAscIdAsc(recipe.getId()).stream()
                        .map(this::ingredientResponse)
                        .toList());
    }

    private ModifierIngredientsResponse modifierResponse(ModifierOptionEntity option) {
        return new ModifierIngredientsResponse(
                option.getId(),
                option.getName(),
                option.getVersion(),
                modifierIngredients.findByModifierOptionIdOrderByDisplayOrderAscIdAsc(option.getId()).stream()
                        .map(this::ingredientResponse)
                        .toList());
    }

    private IngredientResponse ingredientResponse(RecipeIngredientEntity ingredient) {
        return response(
                ingredient.getInventoryItem(),
                ingredient.getQuantity(),
                ingredient.getDisplayOrder());
    }

    private IngredientResponse ingredientResponse(ModifierOptionIngredientEntity ingredient) {
        return response(
                ingredient.getInventoryItem(),
                ingredient.getQuantity(),
                ingredient.getDisplayOrder());
    }

    private IngredientResponse response(InventoryItemEntity item, java.math.BigDecimal quantity, int order) {
        return new IngredientResponse(
                item.getId(),
                item.getCode(),
                item.getName(),
                item.getUnit(),
                quantity,
                order);
    }

    private RecipeEntity save(RecipeEntity recipe) {
        try {
            return recipes.saveAndFlush(recipe);
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw InventoryManagementException.stale();
        } catch (DataIntegrityViolationException exception) {
            throw InventoryManagementException.conflict("A menu item may have only one recipe");
        }
    }

    private void verifyVersion(RecipeEntity recipe, Long suppliedVersion) {
        if (suppliedVersion == null || recipe.getVersion() != suppliedVersion) {
            throw InventoryManagementException.stale();
        }
    }
}
