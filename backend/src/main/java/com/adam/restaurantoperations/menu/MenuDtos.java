package com.adam.restaurantoperations.menu;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public final class MenuDtos {
    private MenuDtos() {}
    public record CategoryWrite(@NotBlank @Size(max=120) String name,@Size(max=1000) String description,@PositiveOrZero int displayOrder) {}
    public record CategoryUpdate(@NotBlank @Size(max=120) String name,@Size(max=1000) String description,@PositiveOrZero int displayOrder,@NotNull @PositiveOrZero Long version) {}
    public record ItemWrite(@NotNull Long categoryId,@NotBlank @Size(max=40) @Pattern(regexp="^[A-Za-z0-9 _-]+$") String code,@NotBlank @Size(max=160) String name,@Size(max=2000) String description,@Schema(description="Non-negative EUR decimal with at most two fractional digits",example="12.50") @NotNull @DecimalMin("0.00") @Digits(integer=10,fraction=2) BigDecimal basePrice,@PositiveOrZero int displayOrder) {}
    public record ItemUpdate(@NotNull Long categoryId,@NotBlank @Size(max=40) @Pattern(regexp="^[A-Za-z0-9 _-]+$") String code,@NotBlank @Size(max=160) String name,@Size(max=2000) String description,@Schema(description="Non-negative EUR decimal with at most two fractional digits",example="12.50") @NotNull @DecimalMin("0.00") @Digits(integer=10,fraction=2) BigDecimal basePrice,@PositiveOrZero int displayOrder,@Schema(description="Optimistic-lock version returned by the latest read") @NotNull @PositiveOrZero Long version) {}
    public record GroupWrite(@NotBlank @Size(max=120) String name,@Size(max=1000) String description,@NotNull SelectionType selectionType,@Min(0) @Max(20) int minimumSelections,@Min(1) @Max(20) int maximumSelections,@PositiveOrZero int displayOrder) {}
    public record GroupUpdate(@NotBlank @Size(max=120) String name,@Size(max=1000) String description,@NotNull SelectionType selectionType,@Min(0) @Max(20) int minimumSelections,@Min(1) @Max(20) int maximumSelections,@PositiveOrZero int displayOrder,@NotNull @PositiveOrZero Long version) {}
    public record OptionWrite(@NotBlank @Size(max=120) String name,@Schema(description="Non-negative EUR decimal adjustment",example="1.50") @NotNull @DecimalMin("0.00") @Digits(integer=10,fraction=2) BigDecimal priceAdjustment,@PositiveOrZero int displayOrder) {}
    public record OptionUpdate(@NotBlank @Size(max=120) String name,@Schema(description="Non-negative EUR decimal adjustment",example="1.50") @NotNull @DecimalMin("0.00") @Digits(integer=10,fraction=2) BigDecimal priceAdjustment,@PositiveOrZero int displayOrder,@Schema(description="Optimistic-lock version returned by the latest read") @NotNull @PositiveOrZero Long version) {}
    public record VersionedFlag(boolean value,@Schema(description="Optimistic-lock version returned by the latest read") @NotNull @PositiveOrZero Long version) {}
    public record Assignment(@NotNull Long modifierGroupId,@PositiveOrZero int displayOrder) {}
    public record Assignments(@NotNull List<@Valid Assignment> assignments,@NotNull @PositiveOrZero Long version) {}
    public record CategoryResponse(Long id,String name,String description,int displayOrder,boolean active,Instant createdAt,Instant updatedAt,long version){static CategoryResponse from(MenuCategoryEntity e){return new CategoryResponse(e.getId(),e.getName(),e.getDescription(),e.getDisplayOrder(),e.isActive(),e.getCreatedAt(),e.getUpdatedAt(),e.getVersion());}}
    public record CategorySummary(Long id,String name,boolean active){static CategorySummary from(MenuCategoryEntity e){return new CategorySummary(e.getId(),e.getName(),e.isActive());}}
    public record AssignmentResponse(Long modifierGroupId,String name,SelectionType selectionType,int minimumSelections,int maximumSelections,int displayOrder,boolean active) {}
    public record ItemResponse(Long id,CategorySummary category,String code,String name,String description,@Schema(description="Exact decimal string",example="12.50") String basePrice,int displayOrder,boolean active,boolean availableForSale,@Schema(description="True only when category, item, and sale-availability flags are active") boolean effectivelyAvailable,List<AssignmentResponse> modifierGroups,Instant createdAt,Instant updatedAt,long version) {}
    public record OptionResponse(Long id,Long modifierGroupId,String name,String priceAdjustment,int displayOrder,boolean active,Instant createdAt,Instant updatedAt,long version){static OptionResponse from(ModifierOptionEntity e){return new OptionResponse(e.getId(),e.getGroup().getId(),e.getName(),e.getPriceAdjustment().toPlainString(),e.getDisplayOrder(),e.isActive(),e.getCreatedAt(),e.getUpdatedAt(),e.getVersion());}}
    public record GroupResponse(Long id,String name,String description,SelectionType selectionType,int minimumSelections,int maximumSelections,int displayOrder,boolean active,long assignedItemCount,List<OptionResponse> options,Instant createdAt,Instant updatedAt,long version) {}
}
