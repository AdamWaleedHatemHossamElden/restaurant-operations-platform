package com.adam.restaurantoperations.orders;

public enum OrderStatus {
    OPEN,
    SUBMITTED,
    COMPLETED,
    CANCELLED;

    public boolean canTransitionTo(OrderStatus target) {
        return switch (this) {
            case OPEN -> target == SUBMITTED || target == CANCELLED;
            case SUBMITTED -> target == COMPLETED || target == CANCELLED;
            case COMPLETED, CANCELLED -> false;
        };
    }

    public boolean isMutable() {
        return this == OPEN;
    }
}
