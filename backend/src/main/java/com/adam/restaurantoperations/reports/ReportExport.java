package com.adam.restaurantoperations.reports;

import java.util.Locale;

public enum ReportExport {
    SALES,
    MENU,
    PAYMENTS,
    RESERVATIONS,
    INVENTORY,
    STAFF;

    public static ReportExport parse(String value) {
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw ReportManagementException.badRequest("Unsupported report export");
        }
    }
}
