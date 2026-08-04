package com.adam.restaurantoperations.auth.persistence;

import java.time.Instant;

import com.adam.restaurantoperations.users.UserEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenEntityTest {

    @Test
    void tracksExpiryRevocationAndRotation() {
        Instant now = Instant.parse("2026-08-04T00:00:00Z");
        var user = new UserEntity("a@example.com", "hash", "A");
        var current = new RefreshTokenEntity(user, "a", "family", now.plusSeconds(60), null, null);
        var replacement = new RefreshTokenEntity(user, "b", "family", now.plusSeconds(120), null, null);

        assertThat(current.isExpired(now)).isFalse();
        assertThat(current.isExpired(now.plusSeconds(60))).isTrue();
        assertThat(current.isRevoked()).isFalse();

        current.rotate(now, replacement);

        assertThat(current.isRevoked()).isTrue();
        assertThat(current.getRevokedAt()).isEqualTo(now);
    }
}
