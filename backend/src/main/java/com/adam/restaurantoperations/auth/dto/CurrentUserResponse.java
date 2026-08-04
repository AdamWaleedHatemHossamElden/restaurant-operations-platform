package com.adam.restaurantoperations.auth.dto;

import java.util.List;

import com.adam.restaurantoperations.users.UserEntity;

public record CurrentUserResponse(
        Long id,
        String email,
        String displayName,
        boolean enabled,
        List<String> roles) {

    public static CurrentUserResponse from(UserEntity user) {
        List<String> roleNames = user.getUserRoles().stream()
                .filter(userRole -> userRole.getRole().isEnabled())
                .map(userRole -> userRole.getRole().getName())
                .sorted()
                .toList();
        return new CurrentUserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.isEnabled(),
                roleNames);
    }
}
