package com.adam.restaurantoperations.inventory;

import java.util.List;

import com.adam.restaurantoperations.auth.service.RequestMetadata;
import com.adam.restaurantoperations.common.config.OpenApiConfiguration;
import com.adam.restaurantoperations.inventory.InventoryDtos.ModifierIngredientsRequest;
import com.adam.restaurantoperations.inventory.InventoryDtos.ModifierIngredientsResponse;
import com.adam.restaurantoperations.inventory.InventoryDtos.RecipeIngredientsRequest;
import com.adam.restaurantoperations.inventory.InventoryDtos.RecipeResponse;
import com.adam.restaurantoperations.inventory.InventoryDtos.RecipeStateRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/recipes")
@Tag(name = "Recipes")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(
        name = OpenApiConfiguration.BEARER_AUTHENTICATION)
public class RecipeController {
    private final RecipeService service;

    public RecipeController(RecipeService service) {
        this.service = service;
    }

    @GetMapping
    public List<RecipeResponse> list() {
        return service.list();
    }

    @GetMapping("/menu-items/{menuItemId}")
    public RecipeResponse get(@PathVariable @Positive Long menuItemId) {
        return service.getByMenuItem(menuItemId);
    }

    @PutMapping("/menu-items/{menuItemId}")
    public RecipeResponse setState(
            @PathVariable @Positive Long menuItemId,
            @Valid @RequestBody RecipeStateRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest) {
        return service.setRecipeState(
                menuItemId,
                request,
                Long.valueOf(jwt.getSubject()),
                RequestMetadata.from(servletRequest).ipAddress());
    }

    @PutMapping("/menu-items/{menuItemId}/ingredients")
    public RecipeResponse ingredients(
            @PathVariable @Positive Long menuItemId,
            @Valid @RequestBody RecipeIngredientsRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest) {
        return service.replaceIngredients(
                menuItemId,
                request,
                Long.valueOf(jwt.getSubject()),
                RequestMetadata.from(servletRequest).ipAddress());
    }

    @GetMapping("/modifier-options/{optionId}/ingredients")
    public ModifierIngredientsResponse modifierIngredients(@PathVariable @Positive Long optionId) {
        return service.getModifierIngredients(optionId);
    }

    @PutMapping("/modifier-options/{optionId}/ingredients")
    public ModifierIngredientsResponse modifierIngredients(
            @PathVariable @Positive Long optionId,
            @Valid @RequestBody ModifierIngredientsRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest) {
        return service.replaceModifierIngredients(
                optionId,
                request,
                Long.valueOf(jwt.getSubject()),
                RequestMetadata.from(servletRequest).ipAddress());
    }
}
