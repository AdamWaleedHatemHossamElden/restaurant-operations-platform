package com.adam.restaurantoperations.orders;

import org.springframework.http.HttpStatus;

public class OrderManagementException extends RuntimeException {
    private final HttpStatus status;

    private OrderManagementException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public static OrderManagementException notFound(String entity) {
        return new OrderManagementException(HttpStatus.NOT_FOUND, entity + " not found");
    }

    public static OrderManagementException badRequest(String message) {
        return new OrderManagementException(HttpStatus.BAD_REQUEST, message);
    }

    public static OrderManagementException conflict(String message) {
        return new OrderManagementException(HttpStatus.CONFLICT, message);
    }

    public static OrderManagementException stale() {
        return conflict("Order was changed by another request; reload and retry");
    }

    public static OrderManagementException immutable() {
        return conflict("Only OPEN orders may be modified");
    }

    public static OrderManagementException contention() {
        return conflict("Order changed concurrently; reload and retry");
    }

    public HttpStatus getStatus() {
        return status;
    }
}
