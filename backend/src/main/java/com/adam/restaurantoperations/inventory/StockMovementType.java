package com.adam.restaurantoperations.inventory;

public enum StockMovementType {
    RECEIPT(1),
    USAGE(-1),
    WASTE(-1),
    ADJUSTMENT_IN(1),
    ADJUSTMENT_OUT(-1);

    private final int sign;

    StockMovementType(int sign) {
        this.sign = sign;
    }

    public int sign() {
        return sign;
    }

    public boolean manual() {
        return this == ADJUSTMENT_IN || this == ADJUSTMENT_OUT || this == WASTE;
    }
}
