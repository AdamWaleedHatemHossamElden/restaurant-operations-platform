package com.adam.restaurantoperations.reservations.dto;

import java.time.Instant;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateReservationRequest(
        @NotBlank @Size(max = 160) String guestName,
        @NotBlank @Size(max = 32)
        @Pattern(regexp = "^[+0-9() .-]{7,32}$", message = "must be a valid phone number") String guestPhone,
        @Email @Size(max = 320) String guestEmail,
        @Min(1) @Max(100) int partySize,
        @NotNull @FutureOrPresent Instant startAt,
        @Min(15) @Max(480) int durationMinutes,
        Long restaurantTableId,
        @Size(max = 2000) String notes) {
}
