package com.adam.restaurantoperations.staff;

import org.springframework.http.HttpStatus;

public class StaffManagementException extends RuntimeException {
    private final HttpStatus status;

    private StaffManagementException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public static StaffManagementException employeeNotFound() {
        return new StaffManagementException(HttpStatus.NOT_FOUND, "Employee not found");
    }

    public static StaffManagementException availabilityNotFound() {
        return new StaffManagementException(HttpStatus.NOT_FOUND, "Employee availability not found");
    }

    public static StaffManagementException shiftNotFound() {
        return new StaffManagementException(HttpStatus.NOT_FOUND, "Shift not found");
    }

    public static StaffManagementException invalidRange() {
        return new StaffManagementException(HttpStatus.BAD_REQUEST, "Start time must precede end time");
    }

    public static StaffManagementException conflict(String message) {
        return new StaffManagementException(HttpStatus.CONFLICT, message);
    }

    public static StaffManagementException contention() {
        return conflict("Staff schedule changed; reload and retry");
    }

    public HttpStatus getStatus() {
        return status;
    }
}
