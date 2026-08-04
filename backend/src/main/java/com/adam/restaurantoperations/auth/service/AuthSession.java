package com.adam.restaurantoperations.auth.service;

import com.adam.restaurantoperations.auth.dto.AuthResponse;

public record AuthSession(AuthResponse response, String refreshToken) {
}
