package com.adam.restaurantoperations.inventory;

import java.math.BigDecimal;
import java.time.Instant;

import com.adam.restaurantoperations.users.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "stock_movements")
public class StockMovementEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_item_id", nullable = false)
    private InventoryItemEntity inventoryItem;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 20)
    private StockMovementType movementType;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private UserEntity actor;

    @Column(name = "reference_type", length = 40)
    private String referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "source_key", unique = true, length = 160)
    private String sourceKey;

    @Column(length = 500)
    private String reason;

    @Column(name = "unit_cost", precision = 12, scale = 4)
    private BigDecimal unitCost;

    @Column(name = "total_cost", precision = 14, scale = 4)
    private BigDecimal totalCost;

    protected StockMovementEntity() {
    }

    public StockMovementEntity(
            InventoryItemEntity inventoryItem,
            StockMovementType movementType,
            BigDecimal quantity,
            Instant occurredAt,
            UserEntity actor,
            String referenceType,
            Long referenceId,
            String sourceKey,
            String reason,
            BigDecimal unitCost,
            BigDecimal totalCost) {
        this.inventoryItem = inventoryItem;
        this.movementType = movementType;
        this.quantity = quantity;
        this.occurredAt = occurredAt;
        this.actor = actor;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.sourceKey = sourceKey;
        this.reason = reason;
        this.unitCost = unitCost;
        this.totalCost = totalCost;
    }

    @PrePersist
    void initializeTimestamp() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public InventoryItemEntity getInventoryItem() {
        return inventoryItem;
    }

    public StockMovementType getMovementType() {
        return movementType;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public Long getReferenceId() {
        return referenceId;
    }

    public String getReason() {
        return reason;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public BigDecimal getTotalCost() {
        return totalCost;
    }
}
