package com.adam.restaurantoperations.users;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailNormalizerTest {

    @Test
    void stripsWhitespaceAndUsesLocaleIndependentLowercase() {
        assertThat(new EmailNormalizer().normalize("  ADAM@Example.COM  "))
                .isEqualTo("adam@example.com");
    }
}
