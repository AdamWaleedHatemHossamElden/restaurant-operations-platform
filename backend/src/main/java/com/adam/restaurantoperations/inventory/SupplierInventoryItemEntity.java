package com.adam.restaurantoperations.inventory;

import java.math.BigDecimal;
import java.time.Instant;

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
import jakarta.persistence.Version;

@Entity
@Table(name = "supplier_inventory_items")
public class SupplierInventoryItemEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private SupplierEntity supplier;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_item_id", nullable = false)
    private InventoryItemEntity inventoryItem;

    @Column(name = "supplier_item_code", length = 80)
    private String supplierItemCode;

    @Column(name = "unit_cost", nullable = false, precision = 12, scale = 4)
    private BigDecimal unitCost;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected SupplierInventoryItemEntity() {
    }

    public SupplierInventoryItemEntity(
            SupplierEntity supplier,
            InventoryItemEntity inventoryItem,
            String supplierItemCode,
            BigDecimal unitCost) {
        this.supplier = supplier;
        this.inventoryItem = inventoryItem;
        this.supplierItemCode = supplierItemCode;
        this.unitCost = unitCost;
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

    public void update(String supplierItemCode, BigDecimal unitCost, boolean active) {
        this.supplierItemCode = supplierItemCode;
        this.unitCost = unitCost;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public SupplierEntity getSupplier() {
        return supplier;
    }

    public InventoryItemEntity getInventoryItem() {
        return inventoryItem;
    }

    public String getSupplierItemCode() {
        return supplierItemCode;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public boolean isActive() {
        return active;
    }

    public long getVersion() {
        return version;
    }
}
