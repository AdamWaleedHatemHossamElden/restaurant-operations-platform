package com.adam.restaurantoperations.payments;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class InvoiceNumberGenerator {
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private final Clock clock;

    public InvoiceNumberGenerator(Clock clock) {
        this.clock = clock;
    }

    public String generate() {
        String day = LocalDate.now(clock.withZone(ZoneOffset.UTC)).format(DATE);
        String entropy = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return ("INV-" + day + "-" + entropy).toUpperCase(Locale.ROOT);
    }
}
