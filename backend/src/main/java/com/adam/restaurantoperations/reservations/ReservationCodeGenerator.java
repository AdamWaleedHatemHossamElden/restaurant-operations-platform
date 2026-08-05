package com.adam.restaurantoperations.reservations;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

@Component
public class ReservationCodeGenerator {

    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int RANDOM_LENGTH = 12;

    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        var code = new StringBuilder("RSV-");
        for (int index = 0; index < RANDOM_LENGTH; index++) {
            code.append(ALPHABET[secureRandom.nextInt(ALPHABET.length)]);
        }
        return code.toString();
    }
}
