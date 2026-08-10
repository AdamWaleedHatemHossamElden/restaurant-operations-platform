package com.adam.restaurantoperations.kitchen;

import java.time.Instant;
import java.util.List;

import com.adam.restaurantoperations.auth.service.RequestMetadata;
import com.adam.restaurantoperations.common.config.OpenApiConfiguration;
import com.adam.restaurantoperations.kitchen.KitchenDtos.KitchenItemStatusRequest;
import com.adam.restaurantoperations.kitchen.KitchenDtos.KitchenTicketResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/kitchen")
@Tag(name = "Kitchen")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(
        name = OpenApiConfiguration.BEARER_AUTHENTICATION)
public class KitchenController {
    private final KitchenService service;

    public KitchenController(KitchenService service) {
        this.service = service;
    }

    @GetMapping("/tickets")
    @Operation(summary = "List authoritative kitchen tickets with allowlisted filtering and sorting")
    public List<KitchenTicketResponse> list(
            @RequestParam(required = false) KitchenTicketStatus status,
            @RequestParam(required = false) @Positive Long tableId,
            @RequestParam(required = false) String orderNumber,
            @RequestParam(required = false) Instant submittedFrom,
            @RequestParam(required = false) Instant submittedTo,
            @RequestParam(defaultValue = "false") boolean includeCancelled,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "ASC") Sort.Direction direction) {
        return service.list(
                status,
                tableId,
                orderNumber,
                submittedFrom,
                submittedTo,
                includeCancelled,
                sortBy,
                direction);
    }

    @GetMapping("/tickets/{id}")
    @Operation(summary = "Get one kitchen ticket using immutable submitted-order snapshots")
    public KitchenTicketResponse get(@PathVariable @Positive Long id) {
        return service.get(id);
    }

    @GetMapping("/orders/{orderId}")
    @Operation(summary = "Get the kitchen ticket for an order")
    public KitchenTicketResponse getByOrder(@PathVariable @Positive Long orderId) {
        return service.getByOrder(orderId);
    }

    @PatchMapping("/tickets/{ticketId}/items/{itemId}/status")
    @Operation(summary = "Progress one kitchen item through its forward-only preparation lifecycle")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Kitchen item progressed"),
        @ApiResponse(responseCode = "400", description = "Validation failed"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "ADMIN role required"),
        @ApiResponse(responseCode = "404", description = "Ticket, item, or order not found"),
        @ApiResponse(responseCode = "409", description = "Stale, cancelled, or invalid state conflict")
    })
    public KitchenTicketResponse transitionItem(
            @PathVariable @Positive Long ticketId,
            @PathVariable @Positive Long itemId,
            @Valid @RequestBody KitchenItemStatusRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest) {
        return service.transitionItem(
                ticketId,
                itemId,
                request,
                Long.valueOf(jwt.getSubject()),
                RequestMetadata.from(servletRequest).ipAddress());
    }
}
