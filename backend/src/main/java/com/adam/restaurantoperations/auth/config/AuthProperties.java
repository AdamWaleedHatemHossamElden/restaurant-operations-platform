package com.adam.restaurantoperations.auth.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        @NotBlank String jwtSecret,
        @NotNull Duration accessTokenTtl,
        @NotNull Duration refreshTokenTtl,
        boolean cookieSecure,
        @NotBlank String cookieSameSite,
        String bootstrapEmail,
        String bootstrapPassword,
        String bootstrapDisplayName) {

    public static final String REFRESH_COOKIE_NAME = "refresh_token";
    public static final String CSRF_HEADER_NAME = "X-CSRF-Protection";
    public static final String CSRF_HEADER_VALUE = "1";

    @PostConstruct
    void validateSecuritySettings() {
        if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET must contain at least 32 UTF-8 bytes");
        }
        if (accessTokenTtl.isZero() || accessTokenTtl.isNegative()) {
            throw new IllegalStateException("JWT access-token lifetime must be positive");
        }
        if (refreshTokenTtl.isZero() || refreshTokenTtl.isNegative()) {
            throw new IllegalStateException("JWT refresh-token lifetime must be positive");
        }
    }
}
