package com.adam.restaurantoperations.tables.dto;

import com.adam.restaurantoperations.tables.TableStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateTableRequest(
        @NotBlank
        @Size(max = 32)
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_-]*$", message = "must contain only letters, numbers, _ or -")
        String tableNumber,
        @NotBlank
        @Size(max = 120)
        String displayName,
        @Min(1)
        @Max(100)
        int capacity,
        @NotBlank
        @Size(max = 80)
        String section,
        @NotNull
        TableStatus status,
        @Schema(description = "Version returned by the most recent read", example = "0")
        @NotNull
        @PositiveOrZero
        Long version) {
}
