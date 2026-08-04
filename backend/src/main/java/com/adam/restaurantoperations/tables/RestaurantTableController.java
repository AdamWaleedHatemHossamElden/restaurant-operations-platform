package com.adam.restaurantoperations.tables;

import java.net.URI;
import java.util.List;

import com.adam.restaurantoperations.auth.service.RequestMetadata;
import com.adam.restaurantoperations.common.config.OpenApiConfiguration;
import com.adam.restaurantoperations.tables.dto.CreateTableRequest;
import com.adam.restaurantoperations.tables.dto.TableActivationRequest;
import com.adam.restaurantoperations.tables.dto.TableResponse;
import com.adam.restaurantoperations.tables.dto.UpdateTableRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
@RequestMapping("/api/v1/tables")
@Tag(name = "Restaurant tables")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(
        name = OpenApiConfiguration.BEARER_AUTHENTICATION)
public class RestaurantTableController {

    private final RestaurantTableService service;

    public RestaurantTableController(RestaurantTableService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List and filter restaurant tables")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tables returned"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "ADMIN role required")
    })
    public List<TableResponse> list(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String section,
            @RequestParam(required = false) TableStatus status,
            @RequestParam(required = false) String tableNumber,
            @RequestParam(defaultValue = "tableNumber") String sortBy,
            @RequestParam(defaultValue = "ASC") Sort.Direction direction) {
        return service.list(active, section, status, tableNumber, sortBy, direction);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a restaurant table")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Table returned"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "ADMIN role required"),
        @ApiResponse(responseCode = "404", description = "Table not found")
    })
    public TableResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @Operation(summary = "Create a restaurant table")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Table created"),
        @ApiResponse(responseCode = "400", description = "Validation failed"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "ADMIN role required"),
        @ApiResponse(responseCode = "409", description = "Table number conflict")
    })
    public ResponseEntity<TableResponse> create(
            @Valid @RequestBody CreateTableRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest) {
        TableResponse response = service.create(
                request,
                actorId(jwt),
                RequestMetadata.from(servletRequest));
        return ResponseEntity.created(URI.create("/api/v1/tables/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a restaurant table")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Table updated"),
        @ApiResponse(responseCode = "400", description = "Validation failed"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "ADMIN role required"),
        @ApiResponse(responseCode = "404", description = "Table not found"),
        @ApiResponse(responseCode = "409", description = "Table number or version conflict")
    })
    public TableResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTableRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest) {
        return service.update(id, request, actorId(jwt), RequestMetadata.from(servletRequest));
    }

    @PatchMapping("/{id}/activation")
    @Operation(summary = "Deactivate or reactivate a restaurant table")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Activation state changed"),
        @ApiResponse(responseCode = "400", description = "Validation failed"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "ADMIN role required"),
        @ApiResponse(responseCode = "404", description = "Table not found"),
        @ApiResponse(responseCode = "409", description = "Version conflict")
    })
    public TableResponse setActivation(
            @PathVariable Long id,
            @Valid @RequestBody TableActivationRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest) {
        return service.setActivation(id, request, actorId(jwt), RequestMetadata.from(servletRequest));
    }

    private Long actorId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
