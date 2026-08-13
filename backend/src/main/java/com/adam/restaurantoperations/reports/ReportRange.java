package com.adam.restaurantoperations.reports;

import java.time.Duration;
import java.time.Instant;

public record ReportRange(Instant from, Instant to) {
    private static final Duration MAXIMUM = Duration.ofDays(366);

    public static ReportRange validated(Instant from, Instant to) {
        if (from == null || to == null) {
            throw ReportManagementException.badRequest("Both from and to are required");
        }
        if (!from.isBefore(to)) {
            throw ReportManagementException.badRequest("from must be before to");
        }
        if (Duration.between(from, to).compareTo(MAXIMUM) > 0) {
            throw ReportManagementException.badRequest("Report range cannot exceed 366 days");
        }
        return new ReportRange(from, to);
    }

    public static int validatedTop(Integer top) {
        int value = top == null ? 10 : top;
        if (value < 1 || value > 100) {
            throw ReportManagementException.badRequest("top must be between 1 and 100");
        }
        return value;
    }
}
