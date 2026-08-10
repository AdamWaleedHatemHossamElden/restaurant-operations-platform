package com.adam.restaurantoperations.kitchen;

public enum KitchenItemStatus {
    QUEUED,
    PREPARING,
    READY;

    public boolean canTransitionTo(KitchenItemStatus target) {
        return switch (this) {
            case QUEUED -> target == PREPARING;
            case PREPARING -> target == READY;
            case READY -> false;
        };
    }
}
