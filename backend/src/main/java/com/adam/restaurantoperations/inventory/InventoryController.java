package com.adam.restaurantoperations.inventory;

import java.util.List;

import com.adam.restaurantoperations.auth.service.RequestMetadata;
import com.adam.restaurantoperations.common.config.OpenApiConfiguration;
import com.adam.restaurantoperations.inventory.InventoryDtos.ActivationRequest;
import com.adam.restaurantoperations.inventory.InventoryDtos.InventoryItemRequest;
import com.adam.restaurantoperations.inventory.InventoryDtos.InventoryItemResponse;
import com.adam.restaurantoperations.inventory.InventoryDtos.ManualMovementRequest;
import com.adam.restaurantoperations.inventory.InventoryDtos.StockMovementResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/inventory")
@Tag(name = "Inventory")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(
        name = OpenApiConfiguration.BEARER_AUTHENTICATION)
public class InventoryController {
    private final InventoryService service;

    public InventoryController(InventoryService service) {
        this.service = service;
    }

    @GetMapping("/items")
    public List<InventoryItemResponse> list(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Boolean lowStock,
            @RequestParam(required = false) InventoryUnit unit,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "ASC") Sort.Direction direction) {
        return service.list(active, lowStock, unit, search, sortBy, direction);
    }

    @GetMapping("/low-stock")
    public List<InventoryItemResponse> lowStock() {
        return service.list(null, true, null, null, "name", Sort.Direction.ASC);
    }

    @GetMapping("/items/{id}")
    public InventoryItemResponse get(@PathVariable @Positive Long id) {
        return service.get(id);
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryItemResponse create(
            @Valid @RequestBody InventoryItemRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest) {
        return service.create(request, actor(jwt), RequestMetadata.from(servletRequest).ipAddress());
    }

    @PutMapping("/items/{id}")
    public InventoryItemResponse update(
            @PathVariable @Positive Long id,
            @Valid @RequestBody InventoryItemRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest) {
        return service.update(id, request, actor(jwt), RequestMetadata.from(servletRequest).ipAddress());
    }

    @PatchMapping("/items/{id}/activation")
    public InventoryItemResponse activation(
            @PathVariable @Positive Long id,
            @Valid @RequestBody ActivationRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest) {
        return service.setActivation(
                id,
                request.value(),
                request.version(),
                actor(jwt),
                RequestMetadata.from(servletRequest).ipAddress());
    }

    @GetMapping("/items/{id}/movements")
    public List<StockMovementResponse> movements(@PathVariable @Positive Long id) {
        return service.movements(id);
    }

    @PostMapping("/movements")
    @ResponseStatus(HttpStatus.CREATED)
    public StockMovementResponse movement(
            @Valid @RequestBody ManualMovementRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest) {
        return service.recordManualMovement(
                request,
                actor(jwt),
                RequestMetadata.from(servletRequest).ipAddress());
    }

    private Long actor(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
