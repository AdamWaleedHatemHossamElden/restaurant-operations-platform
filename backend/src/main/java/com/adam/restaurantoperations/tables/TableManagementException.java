package com.adam.restaurantoperations.tables;

import org.springframework.http.HttpStatus;

public class TableManagementException extends RuntimeException {

    private final HttpStatus status;

    private TableManagementException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public static TableManagementException notFound() {
        return new TableManagementException(HttpStatus.NOT_FOUND, "Restaurant table not found");
    }

    public static TableManagementException duplicateTableNumber() {
        return new TableManagementException(HttpStatus.CONFLICT, "Table number is already in use");
    }

    public static TableManagementException versionConflict() {
        return new TableManagementException(
                HttpStatus.CONFLICT,
                "Restaurant table was changed by another request; reload and retry");
    }

    public HttpStatus getStatus() {
        return status;
    }
}
