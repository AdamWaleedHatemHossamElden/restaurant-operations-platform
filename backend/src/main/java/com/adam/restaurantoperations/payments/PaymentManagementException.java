package com.adam.restaurantoperations.payments;

import org.springframework.http.HttpStatus;

public class PaymentManagementException extends RuntimeException {
    private final HttpStatus status;

    private PaymentManagementException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public static PaymentManagementException badRequest(String message) {
        return new PaymentManagementException(HttpStatus.BAD_REQUEST, message);
    }

    public static PaymentManagementException notFound(String entity) {
        return new PaymentManagementException(HttpStatus.NOT_FOUND, entity + " not found");
    }

    public static PaymentManagementException conflict(String message) {
        return new PaymentManagementException(HttpStatus.CONFLICT, message);
    }

    public static PaymentManagementException contention() {
        return conflict("Payment state changed concurrently; reload and retry");
    }

    public HttpStatus getStatus() {
        return status;
    }
}
