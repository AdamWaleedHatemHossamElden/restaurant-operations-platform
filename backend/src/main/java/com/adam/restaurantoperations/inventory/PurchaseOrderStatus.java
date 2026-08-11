package com.adam.restaurantoperations.inventory;

public enum PurchaseOrderStatus {
    DRAFT,
    ORDERED,
    PARTIALLY_RECEIVED,
    RECEIVED,
    CANCELLED;

    public boolean terminal() {
        return this == RECEIVED || this == CANCELLED;
    }
}
