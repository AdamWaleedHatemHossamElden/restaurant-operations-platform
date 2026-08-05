package com.adam.restaurantoperations.reservations;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import com.adam.restaurantoperations.auth.service.RequestMetadata;
import com.adam.restaurantoperations.common.config.OpenApiConfiguration;
import com.adam.restaurantoperations.reservations.dto.CreateReservationRequest;
import com.adam.restaurantoperations.reservations.dto.ReservationResponse;
import com.adam.restaurantoperations.reservations.dto.ReservationStatusRequest;
import com.adam.restaurantoperations.reservations.dto.TableAvailabilityResponse;
import com.adam.restaurantoperations.reservations.dto.UpdateReservationRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/reservations")
@Tag(name = "Reservations")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(
        name = OpenApiConfiguration.BEARER_AUTHENTICATION)
public class ReservationController {

    private final ReservationService service;

    public ReservationController(ReservationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List and filter reservations")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Reservations returned"),
        @ApiResponse(responseCode = "400", description = "Invalid filter"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "ADMIN role required")
    })
    public List<ReservationResponse> list(
            @RequestParam(required = false) Instant startFrom,
            @RequestParam(required = false) Instant startTo,
            @RequestParam(required = false) ReservationStatus status,
            @RequestParam(required = false) Long tableId,
            @RequestParam(required = false) Boolean assigned,
            @RequestParam(required = false) String guestName,
            @RequestParam(required = false) String reservationCode,
            @RequestParam(defaultValue = "startAt") String sortBy,
            @RequestParam(defaultValue = "ASC") Sort.Direction direction) {
        return service.list(
                startFrom,
                startTo,
                status,
                tableId,
                assigned,
                guestName,
                reservationCode,
                sortBy,
                direction);
    }

    @GetMapping("/availability")
    @Operation(summary = "List tables available for a reservation time window")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Suitable tables returned"),
        @ApiResponse(responseCode = "400", description = "Invalid availability request"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "ADMIN role required")
    })
    public List<TableAvailabilityResponse> availability(
            @RequestParam @NotNull Instant startAt,
            @RequestParam @Min(15) @Max(480) int durationMinutes,
            @RequestParam @Min(1) @Max(100) int partySize,
            @Parameter(description = "Reservation to exclude while editing")
            @RequestParam(required = false) Long excludeReservationId) {
        return service.availability(startAt, durationMinutes, partySize, excludeReservationId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a reservation")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Reservation returned"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "ADMIN role required"),
        @ApiResponse(responseCode = "404", description = "Reservation not found")
    })
    public ReservationResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @Operation(summary = "Create a pending reservation")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Reservation created"),
        @ApiResponse(responseCode = "400", description = "Validation failed"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "ADMIN role required"),
        @ApiResponse(responseCode = "404", description = "Assigned table not found"),
        @ApiResponse(responseCode = "409", description = "Assigned table unavailable")
    })
    public ResponseEntity<ReservationResponse> create(
            @Valid @RequestBody CreateReservationRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest) {
        ReservationResponse response = service.create(
                request,
                actorId(jwt),
                RequestMetadata.from(servletRequest));
        return ResponseEntity.created(URI.create("/api/v1/reservations/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update reservation details and assignment")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Reservation updated"),
        @ApiResponse(responseCode = "400", description = "Validation failed"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "ADMIN role required"),
        @ApiResponse(responseCode = "404", description = "Reservation or table not found"),
        @ApiResponse(responseCode = "409", description = "Availability, terminal, or version conflict")
    })
    public ReservationResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateReservationRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest) {
        return service.update(id, request, actorId(jwt), RequestMetadata.from(servletRequest));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Apply a valid reservation status transition")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Reservation status changed"),
        @ApiResponse(responseCode = "400", description = "Validation failed"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "ADMIN role required"),
        @ApiResponse(responseCode = "404", description = "Reservation not found"),
        @ApiResponse(responseCode = "409", description = "Transition, availability, or version conflict")
    })
    public ReservationResponse transition(
            @PathVariable Long id,
            @Valid @RequestBody ReservationStatusRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest) {
        return service.transition(id, request, actorId(jwt), RequestMetadata.from(servletRequest));
    }

    private Long actorId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
