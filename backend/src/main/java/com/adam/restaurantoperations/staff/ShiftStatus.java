package com.adam.restaurantoperations.staff;

public enum ShiftStatus {
    SCHEDULED,
    COMPLETED,
    CANCELLED;

    public boolean isTerminal() {
        return this != SCHEDULED;
    }
}
