package com.adam.restaurantoperations.auth.web;

import com.adam.restaurantoperations.auth.config.AuthProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class RefreshCookieService {

    private static final String COOKIE_PATH = "/api/v1/auth";

    private final AuthProperties properties;

    public RefreshCookieService(AuthProperties properties) {
        this.properties = properties;
    }

    public ResponseCookie create(String rawRefreshToken) {
        return ResponseCookie.from(AuthProperties.REFRESH_COOKIE_NAME, rawRefreshToken)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite(properties.cookieSameSite())
                .path(COOKIE_PATH)
                .maxAge(properties.refreshTokenTtl())
                .build();
    }

    public ResponseCookie clear() {
        return ResponseCookie.from(AuthProperties.REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite(properties.cookieSameSite())
                .path(COOKIE_PATH)
                .maxAge(0)
                .build();
    }
}
