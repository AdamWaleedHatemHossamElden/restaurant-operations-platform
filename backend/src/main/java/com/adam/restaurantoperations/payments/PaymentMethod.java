package com.adam.restaurantoperations.payments;

public enum PaymentMethod {
    CASH,
    CARD,
    BANK_TRANSFER,
    OTHER;

    public boolean requiresExternalReference() {
        return this == CARD || this == BANK_TRANSFER;
    }
}
