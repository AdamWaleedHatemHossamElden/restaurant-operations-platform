package com.adam.restaurantoperations.tables.dto;

import com.adam.restaurantoperations.tables.TableStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateTableRequest(
        @Schema(example = "T-01")
        @NotBlank
        @Size(max = 32)
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_-]*$", message = "must contain only letters, numbers, _ or -")
        String tableNumber,
        @Schema(example = "Window table 1")
        @NotBlank
        @Size(max = 120)
        String displayName,
        @Schema(example = "4")
        @Min(1)
        @Max(100)
        int capacity,
        @Schema(example = "Main dining")
        @NotBlank
        @Size(max = 80)
        String section,
        @Schema(example = "AVAILABLE")
        @NotNull
        TableStatus status) {
}
