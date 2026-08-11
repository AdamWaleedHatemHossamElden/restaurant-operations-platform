package com.adam.restaurantoperations.inventory;

import org.springframework.http.HttpStatus;

public class InventoryManagementException extends RuntimeException {
    private final HttpStatus status;

    private InventoryManagementException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public static InventoryManagementException badRequest(String message) {
        return new InventoryManagementException(HttpStatus.BAD_REQUEST, message);
    }

    public static InventoryManagementException notFound(String resource) {
        return new InventoryManagementException(HttpStatus.NOT_FOUND, resource + " was not found");
    }

    public static InventoryManagementException conflict(String message) {
        return new InventoryManagementException(HttpStatus.CONFLICT, message);
    }

    public static InventoryManagementException stale() {
        return conflict("Inventory record changed by another request; reload and retry");
    }

    public HttpStatus getStatus() {
        return status;
    }
}
