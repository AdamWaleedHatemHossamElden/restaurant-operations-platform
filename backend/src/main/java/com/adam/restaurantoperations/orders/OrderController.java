package com.adam.restaurantoperations.orders;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import com.adam.restaurantoperations.auth.service.RequestMetadata;
import com.adam.restaurantoperations.common.config.OpenApiConfiguration;
import com.adam.restaurantoperations.orders.OrderDtos.AddOrderItemRequest;
import com.adam.restaurantoperations.orders.OrderDtos.CreateOrderRequest;
import com.adam.restaurantoperations.orders.OrderDtos.OrderResponse;
import com.adam.restaurantoperations.orders.OrderDtos.OrderStatusRequest;
import com.adam.restaurantoperations.orders.OrderDtos.StatusHistoryResponse;
import com.adam.restaurantoperations.orders.OrderDtos.UpdateOrderItemRequest;
import com.adam.restaurantoperations.orders.OrderDtos.UpdateOrderRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(
        name = OpenApiConfiguration.BEARER_AUTHENTICATION)
public class OrderController {
    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List orders with allowlisted filtering and sorting")
    public List<OrderResponse> list(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) Long tableId,
            @RequestParam(required = false) Long reservationId,
            @RequestParam(required = false) String orderNumber,
            @RequestParam(required = false) Instant createdFrom,
            @RequestParam(required = false) Instant createdTo,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction) {
        return service.list(
                status,
                tableId,
                reservationId,
                orderNumber,
                createdFrom,
                createdTo,
                sortBy,
                direction);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an order using immutable stored item and modifier snapshots")
    public OrderResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Get chronological business-visible order status history")
    public List<StatusHistoryResponse> history(@PathVariable Long id) {
        return service.get(id).history();
    }

    @PostMapping
    @Operation(
            summary = "Create an OPEN order",
            description = "Requires an active AVAILABLE table and an optional matching SEATED reservation")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Order created"),
        @ApiResponse(responseCode = "400", description = "Validation failed"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "ADMIN role required"),
        @ApiResponse(responseCode = "404", description = "Table or reservation not found"),
        @ApiResponse(responseCode = "409", description = "Table or reservation state conflict")
    })
    public ResponseEntity<OrderResponse> create(
            @Valid @RequestBody CreateOrderRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest) {
        OrderResponse response = service.create(request, actorId(jwt), RequestMetadata.from(servletRequest));
        return ResponseEntity.created(URI.create("/api/v1/orders/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update OPEN-order table, reservation, or notes using its current version")
    public OrderResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrderRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest) {
        return service.update(id, request, actorId(jwt), RequestMetadata.from(servletRequest));
    }

    @PostMapping("/{id}/items")
    @Operation(
            summary = "Add a server-priced item to an OPEN order",
            description = "Names and decimal prices are snapshotted from the current menu; client totals are ignored")
    public OrderResponse addItem(
            @PathVariable Long id,
            @Valid @RequestBody AddOrderItemRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest) {
        return service.addItem(id, request, actorId(jwt), RequestMetadata.from(servletRequest));
    }

    @PutMapping("/{id}/items/{itemId}")
    @Operation(
            summary = "Update an OPEN-order item",
            description = "Omit modifierSelections to preserve pricing; supply it to fully reprice from the current menu")
    public OrderResponse updateItem(
            @PathVariable Long id,
            @PathVariable Long itemId,
            @Valid @RequestBody UpdateOrderItemRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest) {
        return service.updateItem(id, itemId, request, actorId(jwt), RequestMetadata.from(servletRequest));
    }

    @DeleteMapping("/{id}/items/{itemId}")
    @Operation(summary = "Remove an item from an OPEN order and recalculate totals")
    public OrderResponse removeItem(
            @PathVariable Long id,
            @PathVariable Long itemId,
            @RequestParam @NotNull @PositiveOrZero Long version,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest) {
        return service.removeItem(
                id,
                itemId,
                version,
                actorId(jwt),
                RequestMetadata.from(servletRequest));
    }

    @PatchMapping("/{id}/status")
    @Operation(
            summary = "Submit, complete, or cancel an order",
            description = "SUBMITTED orders and their commercial snapshots are immutable")
    public OrderResponse transition(
            @PathVariable Long id,
            @Valid @RequestBody OrderStatusRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest) {
        return service.transition(id, request, actorId(jwt), RequestMetadata.from(servletRequest));
    }

    private Long actorId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
