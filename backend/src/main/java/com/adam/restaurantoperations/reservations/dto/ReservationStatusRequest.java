package com.adam.restaurantoperations.reservations.dto;

import com.adam.restaurantoperations.reservations.ReservationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ReservationStatusRequest(
        @NotNull ReservationStatus status,
        @NotNull @PositiveOrZero Long version) {
}
