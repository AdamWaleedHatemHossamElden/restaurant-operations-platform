package com.adam.restaurantoperations.kitchen;

import org.springframework.http.HttpStatus;

public class KitchenManagementException extends RuntimeException {
    private final HttpStatus status;

    private KitchenManagementException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public static KitchenManagementException badRequest(String message) {
        return new KitchenManagementException(HttpStatus.BAD_REQUEST, message);
    }

    public static KitchenManagementException notFound(String resource) {
        return new KitchenManagementException(HttpStatus.NOT_FOUND, resource + " was not found");
    }

    public static KitchenManagementException conflict(String message) {
        return new KitchenManagementException(HttpStatus.CONFLICT, message);
    }

    public static KitchenManagementException stale() {
        return conflict("Kitchen ticket changed by another request; reload and retry");
    }

    public HttpStatus getStatus() {
        return status;
    }
}
