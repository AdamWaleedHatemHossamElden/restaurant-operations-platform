package com.adam.restaurantoperations.inventory;

import java.math.BigDecimal;
import java.time.Instant;

import com.adam.restaurantoperations.menu.ModifierOptionEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "modifier_option_ingredients")
public class ModifierOptionIngredientEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "modifier_option_id", nullable = false)
    private ModifierOptionEntity modifierOption;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_item_id", nullable = false)
    private InventoryItemEntity inventoryItem;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ModifierOptionIngredientEntity() {
    }

    public ModifierOptionIngredientEntity(
            ModifierOptionEntity modifierOption,
            InventoryItemEntity inventoryItem,
            BigDecimal quantity,
            int displayOrder) {
        this.modifierOption = modifierOption;
        this.inventoryItem = inventoryItem;
        this.quantity = quantity;
        this.displayOrder = displayOrder;
    }

    @PrePersist
    void initializeTimestamps() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = Instant.now();
    }

    public InventoryItemEntity getInventoryItem() {
        return inventoryItem;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }
}
