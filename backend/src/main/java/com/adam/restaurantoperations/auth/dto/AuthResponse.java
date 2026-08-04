package com.adam.restaurantoperations.auth.dto;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        CurrentUserResponse user) {
}
