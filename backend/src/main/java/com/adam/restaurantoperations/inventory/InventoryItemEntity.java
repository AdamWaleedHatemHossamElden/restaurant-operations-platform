package com.adam.restaurantoperations.inventory;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "inventory_items")
public class InventoryItemEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, unique = true, length = 160)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InventoryUnit unit;

    @Column(name = "reorder_threshold", nullable = false, precision = 14, scale = 3)
    private BigDecimal reorderThreshold;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected InventoryItemEntity() {
    }

    public InventoryItemEntity(String code, String name, InventoryUnit unit, BigDecimal reorderThreshold) {
        this.code = code;
        this.name = name;
        this.unit = unit;
        this.reorderThreshold = reorderThreshold;
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

    public void update(String code, String name, BigDecimal reorderThreshold) {
        this.code = code;
        this.name = name;
        this.reorderThreshold = reorderThreshold;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public InventoryUnit getUnit() {
        return unit;
    }

    public BigDecimal getReorderThreshold() {
        return reorderThreshold;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
