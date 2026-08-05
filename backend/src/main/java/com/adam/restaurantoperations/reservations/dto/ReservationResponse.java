package com.adam.restaurantoperations.reservations.dto;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.adam.restaurantoperations.reservations.ReservationEntity;
import com.adam.restaurantoperations.reservations.ReservationStatus;

public record ReservationResponse(
        Long id,
        String reservationCode,
        String guestName,
        String guestPhone,
        String guestEmail,
        int partySize,
        Instant startAt,
        Instant endAt,
        int durationMinutes,
        AssignedTableResponse restaurantTable,
        ReservationStatus status,
        String notes,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    public static ReservationResponse from(ReservationEntity reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getReservationCode(),
                reservation.getGuestName(),
                reservation.getGuestPhone(),
                reservation.getGuestEmail(),
                reservation.getPartySize(),
                reservation.getStartAt(),
                reservation.getStartAt().plus(reservation.getDurationMinutes(), ChronoUnit.MINUTES),
                reservation.getDurationMinutes(),
                AssignedTableResponse.from(reservation.getRestaurantTable()),
                reservation.getStatus(),
                reservation.getNotes(),
                reservation.getCreatedAt(),
                reservation.getUpdatedAt(),
                reservation.getVersion());
    }
}
