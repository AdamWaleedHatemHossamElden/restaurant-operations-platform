package com.adam.restaurantoperations.staff;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public final class StaffDtos {
    private StaffDtos() {
    }

    public record EmployeeRequest(
            @NotBlank @Size(max = 40)
            @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]*", message = "must use letters, numbers, _ or -")
            String employeeCode,
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @Email @Size(max = 254) String email,
            @Size(max = 40) String phone,
            @NotNull OperationalRole defaultOperationalRole,
            LocalDate employmentStartDate,
            @PositiveOrZero Long version) {
    }

    public record ActivationRequest(
            @NotNull Boolean active,
            @NotNull @PositiveOrZero Long version) {
    }

    public record EmployeeResponse(
            Long id,
            String employeeCode,
            String firstName,
            String lastName,
            String email,
            String phone,
            OperationalRole defaultOperationalRole,
            LocalDate employmentStartDate,
            boolean active,
            long version,
            Instant createdAt,
            Instant updatedAt) {
        static EmployeeResponse from(EmployeeEntity employee) {
            return new EmployeeResponse(
                    employee.getId(),
                    employee.getEmployeeCode(),
                    employee.getFirstName(),
                    employee.getLastName(),
                    employee.getEmail(),
                    employee.getPhone(),
                    employee.getDefaultOperationalRole(),
                    employee.getEmploymentStartDate(),
                    employee.isActive(),
                    employee.getVersion(),
                    employee.getCreatedAt(),
                    employee.getUpdatedAt());
        }
    }

    public record AvailabilityRequest(
            @NotNull Instant startAt,
            @NotNull Instant endAt,
            @Size(max = 500) String notes,
            @PositiveOrZero Long version) {
    }

    public record AvailabilityResponse(
            Long id,
            Long employeeId,
            Instant startAt,
            Instant endAt,
            String notes,
            long version,
            Instant createdAt,
            Instant updatedAt) {
        static AvailabilityResponse from(EmployeeAvailabilityEntity availability) {
            return new AvailabilityResponse(
                    availability.getId(),
                    availability.getEmployee().getId(),
                    availability.getStartAt(),
                    availability.getEndAt(),
                    availability.getNotes(),
                    availability.getVersion(),
                    availability.getCreatedAt(),
                    availability.getUpdatedAt());
        }
    }

    public record ShiftRequest(
            @NotNull Long employeeId,
            @NotNull OperationalRole operationalRole,
            @NotNull Instant startAt,
            @NotNull Instant endAt,
            @Size(max = 1000) String notes,
            @PositiveOrZero Long version) {
    }

    public record ShiftStatusRequest(
            @NotNull ShiftStatus status,
            @NotNull @PositiveOrZero Long version) {
    }

    public record EmployeeSummary(
            Long id,
            String employeeCode,
            String firstName,
            String lastName,
            OperationalRole defaultOperationalRole,
            boolean active) {
        static EmployeeSummary from(EmployeeEntity employee) {
            return new EmployeeSummary(
                    employee.getId(),
                    employee.getEmployeeCode(),
                    employee.getFirstName(),
                    employee.getLastName(),
                    employee.getDefaultOperationalRole(),
                    employee.isActive());
        }
    }

    public record ShiftResponse(
            Long id,
            EmployeeSummary employee,
            OperationalRole operationalRole,
            Instant startAt,
            Instant endAt,
            long durationMinutes,
            ShiftStatus status,
            String notes,
            Instant completedAt,
            Instant cancelledAt,
            long version,
            Instant createdAt,
            Instant updatedAt) {
        static ShiftResponse from(ShiftEntity shift) {
            return new ShiftResponse(
                    shift.getId(),
                    EmployeeSummary.from(shift.getEmployee()),
                    shift.getOperationalRole(),
                    shift.getStartAt(),
                    shift.getEndAt(),
                    Duration.between(shift.getStartAt(), shift.getEndAt()).toMinutes(),
                    shift.getStatus(),
                    shift.getNotes(),
                    shift.getCompletedAt(),
                    shift.getCancelledAt(),
                    shift.getVersion(),
                    shift.getCreatedAt(),
                    shift.getUpdatedAt());
        }
    }
}
