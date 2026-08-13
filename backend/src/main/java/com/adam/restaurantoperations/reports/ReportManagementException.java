package com.adam.restaurantoperations.reports;

import org.springframework.http.HttpStatus;

public class ReportManagementException extends RuntimeException {
    private final HttpStatus status;

    private ReportManagementException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public static ReportManagementException badRequest(String message) {
        return new ReportManagementException(HttpStatus.BAD_REQUEST, message);
    }

    public HttpStatus getStatus() {
        return status;
    }
}
