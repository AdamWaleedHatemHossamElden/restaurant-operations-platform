package com.adam.restaurantoperations.auth.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordAndTokenSecurityTest {

    @Test
    void bcryptHashesAndMatchesWithoutStoringPlaintext() {
        var encoder = new BCryptPasswordEncoder(12);
        String hash = encoder.encode("correct horse battery staple");

        assertThat(hash).startsWith("$2");
        assertThat(hash.split("\\$")[2]).isEqualTo("12");
        assertThat(hash).doesNotContain("correct horse battery staple");
        assertThat(encoder.matches("correct horse battery staple", hash)).isTrue();
        assertThat(encoder.matches("wrong", hash)).isFalse();
    }

    @Test
    void refreshTokenHashIsStableSha256AndDoesNotExposeToken() {
        var hasher = new RefreshTokenHasher();

        String hash = hasher.hash("opaque-refresh-token");

        assertThat(hash).hasSize(64);
        assertThat(hash).isEqualTo(hasher.hash("opaque-refresh-token"));
        assertThat(hash).isNotEqualTo(hasher.hash("different-token"));
        assertThat(hash).doesNotContain("opaque-refresh-token");
    }
}
