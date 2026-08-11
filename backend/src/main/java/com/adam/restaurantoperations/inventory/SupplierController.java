package com.adam.restaurantoperations.inventory;

import java.util.List;

import com.adam.restaurantoperations.auth.service.RequestMetadata;
import com.adam.restaurantoperations.common.config.OpenApiConfiguration;
import com.adam.restaurantoperations.inventory.InventoryDtos.ActivationRequest;
import com.adam.restaurantoperations.inventory.InventoryDtos.SupplierItemRequest;
import com.adam.restaurantoperations.inventory.InventoryDtos.SupplierItemResponse;
import com.adam.restaurantoperations.inventory.InventoryDtos.SupplierRequest;
import com.adam.restaurantoperations.inventory.InventoryDtos.SupplierResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
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
@RequestMapping("/api/v1/suppliers")
@Tag(name = "Suppliers")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(
        name = OpenApiConfiguration.BEARER_AUTHENTICATION)
public class SupplierController {
    private final SupplierService service;

    public SupplierController(SupplierService service) {
        this.service = service;
    }

    @GetMapping
    public List<SupplierResponse> list(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String search) {
        return service.list(active, search);
    }

    @GetMapping("/{id}")
    public SupplierResponse get(@PathVariable @Positive Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SupplierResponse create(
            @Valid @RequestBody SupplierRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest) {
        return service.create(request, actor(jwt), RequestMetadata.from(servletRequest).ipAddress());
    }

    @PutMapping("/{id}")
    public SupplierResponse update(
            @PathVariable @Positive Long id,
            @Valid @RequestBody SupplierRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest) {
        return service.update(id, request, actor(jwt), RequestMetadata.from(servletRequest).ipAddress());
    }

    @PatchMapping("/{id}/activation")
    public SupplierResponse activation(
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

    @PutMapping("/{id}/items/{inventoryItemId}")
    public SupplierItemResponse item(
            @PathVariable @Positive Long id,
            @PathVariable @Positive Long inventoryItemId,
            @Valid @RequestBody SupplierItemRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest) {
        if (!inventoryItemId.equals(request.inventoryItemId())) {
            throw InventoryManagementException.badRequest("Inventory item path and body must match");
        }
        return service.upsertItem(
                id,
                request,
                actor(jwt),
                RequestMetadata.from(servletRequest).ipAddress());
    }

    private Long actor(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
