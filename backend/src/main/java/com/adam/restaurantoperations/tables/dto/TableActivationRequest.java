package com.adam.restaurantoperations.tables.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record TableActivationRequest(
        boolean active,
        @Schema(description = "Version returned by the most recent read", example = "0")
        @NotNull
        @PositiveOrZero
        Long version) {
}
