package com.adam.restaurantoperations.inventory;

import java.util.List;

import com.adam.restaurantoperations.auth.service.RequestMetadata;
import com.adam.restaurantoperations.common.config.OpenApiConfiguration;
import com.adam.restaurantoperations.inventory.InventoryDtos.PurchaseOrderLineRequest;
import com.adam.restaurantoperations.inventory.InventoryDtos.PurchaseOrderLineUpdateRequest;
import com.adam.restaurantoperations.inventory.InventoryDtos.PurchaseOrderRequest;
import com.adam.restaurantoperations.inventory.InventoryDtos.PurchaseOrderResponse;
import com.adam.restaurantoperations.inventory.InventoryDtos.PurchaseOrderStatusRequest;
import com.adam.restaurantoperations.inventory.InventoryDtos.PurchaseReceiptRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/purchase-orders")
@Tag(name = "Purchase orders")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(
        name = OpenApiConfiguration.BEARER_AUTHENTICATION)
public class PurchaseOrderController {
    private final PurchaseOrderService service;

    public PurchaseOrderController(PurchaseOrderService service) {
        this.service = service;
    }

    @GetMapping
    public List<PurchaseOrderResponse> list(
            @RequestParam(required = false) PurchaseOrderStatus status,
            @RequestParam(required = false) @Positive Long supplierId,
            @RequestParam(required = false) String search) {
        return service.list(status, supplierId, search);
    }

    @GetMapping("/{id}")
    public PurchaseOrderResponse get(@PathVariable @Positive Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseOrderResponse create(
            @Valid @RequestBody PurchaseOrderRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest) {
        return service.create(request, actor(jwt), RequestMetadata.from(servletRequest).ipAddress());
    }

    @PutMapping("/{id}")
    public PurchaseOrderResponse update(
            @PathVariable @Positive Long id,
            @Valid @RequestBody PurchaseOrderRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest) {
        return service.update(id, request, actor(jwt), RequestMetadata.from(servletRequest).ipAddress());
    }

    @PostMapping("/{id}/items")
    public PurchaseOrderResponse addItem(
            @PathVariable @Positive Long id,
            @Valid @RequestBody PurchaseOrderLineRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest) {
        return service.addLine(id, request, actor(jwt), RequestMetadata.from(servletRequest).ipAddress());
    }

    @PutMapping("/{id}/items/{itemId}")
    public PurchaseOrderResponse updateItem(
            @PathVariable @Positive Long id,
            @PathVariable @Positive Long itemId,
            @Valid @RequestBody PurchaseOrderLineUpdateRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest) {
        return service.updateLine(
                id, itemId, request, actor(jwt), RequestMetadata.from(servletRequest).ipAddress());
    }

    @DeleteMapping("/{id}/items/{itemId}")
    public PurchaseOrderResponse removeItem(
            @PathVariable @Positive Long id,
            @PathVariable @Positive Long itemId,
            @RequestParam @PositiveOrZero Long version,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest) {
        return service.removeLine(
                id, itemId, version, actor(jwt), RequestMetadata.from(servletRequest).ipAddress());
    }

    @PatchMapping("/{id}/status")
    public PurchaseOrderResponse status(
            @PathVariable @Positive Long id,
            @Valid @RequestBody PurchaseOrderStatusRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest) {
        return service.transition(id, request, actor(jwt), RequestMetadata.from(servletRequest).ipAddress());
    }

    @PostMapping("/{id}/receipts")
    public PurchaseOrderResponse receive(
            @PathVariable @Positive Long id,
            @Valid @RequestBody PurchaseReceiptRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest) {
        return service.receive(id, request, actor(jwt), RequestMetadata.from(servletRequest).ipAddress());
    }

    private Long actor(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
