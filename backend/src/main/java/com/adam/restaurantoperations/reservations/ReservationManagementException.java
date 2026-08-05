package com.adam.restaurantoperations.reservations;

import org.springframework.http.HttpStatus;

public class ReservationManagementException extends RuntimeException {

    private final HttpStatus status;

    private ReservationManagementException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public static ReservationManagementException notFound() {
        return new ReservationManagementException(HttpStatus.NOT_FOUND, "Reservation not found");
    }

    public static ReservationManagementException tableNotFound() {
        return new ReservationManagementException(HttpStatus.NOT_FOUND, "Restaurant table not found");
    }

    public static ReservationManagementException tableUnavailable(String message) {
        return new ReservationManagementException(HttpStatus.CONFLICT, message);
    }

    public static ReservationManagementException invalidTransition() {
        return new ReservationManagementException(HttpStatus.CONFLICT, "Reservation status transition is not allowed");
    }

    public static ReservationManagementException terminalReservation() {
        return new ReservationManagementException(HttpStatus.CONFLICT, "Terminal reservations cannot be edited");
    }

    public static ReservationManagementException versionConflict() {
        return new ReservationManagementException(
                HttpStatus.CONFLICT,
                "Reservation was changed by another request; reload and retry");
    }

    public static ReservationManagementException codeConflict() {
        return new ReservationManagementException(HttpStatus.CONFLICT, "Reservation code conflict; retry");
    }

    public static ReservationManagementException contention() {
        return new ReservationManagementException(
                HttpStatus.CONFLICT,
                "Reservation availability changed; reload and retry");
    }

    public static ReservationManagementException invalidRange() {
        return new ReservationManagementException(HttpStatus.BAD_REQUEST, "Start of range must precede end of range");
    }

    public HttpStatus getStatus() {
        return status;
    }
}
