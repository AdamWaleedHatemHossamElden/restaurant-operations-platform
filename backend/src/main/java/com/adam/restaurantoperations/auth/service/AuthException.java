package com.adam.restaurantoperations.auth.service;

import org.springframework.http.HttpStatus;

public class AuthException extends RuntimeException {

    private final HttpStatus status;

    private AuthException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public static AuthException invalidCredentials() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }

    public static AuthException invalidRefreshToken() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
    }

    public static AuthException disabledAccount() {
        return new AuthException(HttpStatus.FORBIDDEN, "Account is disabled");
    }

    public static AuthException invalidAccessToken() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "Invalid access token");
    }

    public HttpStatus getStatus() {
        return status;
    }
}
