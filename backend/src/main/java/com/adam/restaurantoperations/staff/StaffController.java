package com.adam.restaurantoperations.staff;

import java.time.Instant;
import java.util.List;

import com.adam.restaurantoperations.auth.service.RequestMetadata;
import com.adam.restaurantoperations.common.config.OpenApiConfiguration;
import com.adam.restaurantoperations.staff.StaffDtos.ActivationRequest;
import com.adam.restaurantoperations.staff.StaffDtos.AvailabilityRequest;
import com.adam.restaurantoperations.staff.StaffDtos.AvailabilityResponse;
import com.adam.restaurantoperations.staff.StaffDtos.EmployeeRequest;
import com.adam.restaurantoperations.staff.StaffDtos.EmployeeResponse;
import com.adam.restaurantoperations.staff.StaffDtos.ShiftRequest;
import com.adam.restaurantoperations.staff.StaffDtos.ShiftResponse;
import com.adam.restaurantoperations.staff.StaffDtos.ShiftStatusRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.data.domain.Sort;
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
@RequestMapping("/api/v1/staff")
@Tag(name = "Staff scheduling")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(
        name = OpenApiConfiguration.BEARER_AUTHENTICATION)
public class StaffController {
    private final StaffService service;

    public StaffController(StaffService service) {
        this.service = service;
    }

    @GetMapping("/employees")
    public List<EmployeeResponse> employees(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) OperationalRole operationalRole,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "lastName") String sortBy,
            @RequestParam(defaultValue = "ASC") Sort.Direction direction) {
        return service.listEmployees(search, operationalRole, active, sortBy, direction);
    }

    @GetMapping("/employees/{id}")
    public EmployeeResponse employee(@PathVariable @Positive Long id) {
        return service.getEmployee(id);
    }

    @PostMapping("/employees")
    @ResponseStatus(HttpStatus.CREATED)
    public EmployeeResponse createEmployee(
            @Valid @RequestBody EmployeeRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest) {
        return service.createEmployee(request, actor(jwt), ip(servletRequest));
    }

    @PutMapping("/employees/{id}")
    public EmployeeResponse updateEmployee(
            @PathVariable @Positive Long id,
            @Valid @RequestBody EmployeeRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest) {
        return service.updateEmployee(id, request, actor(jwt), ip(servletRequest));
    }

    @PatchMapping("/employees/{id}/activation")
    public EmployeeResponse activateEmployee(
            @PathVariable @Positive Long id,
            @Valid @RequestBody ActivationRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest) {
        return service.setEmployeeActivation(id, request, actor(jwt), ip(servletRequest));
    }

    @GetMapping("/employees/{employeeId}/availability")
    public List<AvailabilityResponse> availability(
            @PathVariable @Positive Long employeeId,
            @RequestParam Instant startAt,
            @RequestParam Instant endAt) {
        return service.listAvailability(employeeId, startAt, endAt);
    }

    @PostMapping("/employees/{employeeId}/availability")
    @ResponseStatus(HttpStatus.CREATED)
    public AvailabilityResponse createAvailability(
            @PathVariable @Positive Long employeeId,
            @Valid @RequestBody AvailabilityRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest) {
        return service.createAvailability(employeeId, request, actor(jwt), ip(servletRequest));
    }

    @PutMapping("/employees/{employeeId}/availability/{id}")
    public AvailabilityResponse updateAvailability(
            @PathVariable @Positive Long employeeId,
            @PathVariable @Positive Long id,
            @Valid @RequestBody AvailabilityRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest) {
        return service.updateAvailability(employeeId, id, request, actor(jwt), ip(servletRequest));
    }

    @DeleteMapping("/employees/{employeeId}/availability/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeAvailability(
            @PathVariable @Positive Long employeeId,
            @PathVariable @Positive Long id,
            @RequestParam @PositiveOrZero long version,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest) {
        service.removeAvailability(employeeId, id, version, actor(jwt), ip(servletRequest));
    }

    @GetMapping("/shifts")
    public List<ShiftResponse> shifts(
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) OperationalRole operationalRole,
            @RequestParam(required = false) ShiftStatus status,
            @RequestParam(required = false) Instant startFrom,
            @RequestParam(required = false) Instant startTo,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "startAt") String sortBy,
            @RequestParam(defaultValue = "ASC") Sort.Direction direction) {
        return service.listShifts(
                employeeId,
                operationalRole,
                status,
                startFrom,
                startTo,
                search,
                sortBy,
                direction);
    }

    @GetMapping("/shifts/{id}")
    public ShiftResponse shift(@PathVariable @Positive Long id) {
        return service.getShift(id);
    }

    @PostMapping("/shifts")
    @ResponseStatus(HttpStatus.CREATED)
    public ShiftResponse createShift(
            @Valid @RequestBody ShiftRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest) {
        return service.createShift(request, actor(jwt), ip(servletRequest));
    }

    @PutMapping("/shifts/{id}")
    public ShiftResponse updateShift(
            @PathVariable @Positive Long id,
            @Valid @RequestBody ShiftRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest) {
        return service.updateShift(id, request, actor(jwt), ip(servletRequest));
    }

    @PatchMapping("/shifts/{id}/status")
    public ShiftResponse transitionShift(
            @PathVariable @Positive Long id,
            @Valid @RequestBody ShiftStatusRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest) {
        return service.transitionShift(id, request, actor(jwt), ip(servletRequest));
    }

    private Long actor(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }

    private String ip(HttpServletRequest request) {
        return RequestMetadata.from(request).ipAddress();
    }
}
