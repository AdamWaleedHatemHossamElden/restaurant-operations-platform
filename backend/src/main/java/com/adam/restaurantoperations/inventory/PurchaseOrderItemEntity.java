package com.adam.restaurantoperations.inventory;

import java.math.BigDecimal;
import java.time.Instant;

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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "purchase_order_items")
public class PurchaseOrderItemEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrderEntity purchaseOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_item_id", nullable = false)
    private InventoryItemEntity inventoryItem;

    @Column(name = "inventory_code_snapshot", nullable = false, length = 40)
    private String inventoryCodeSnapshot;

    @Column(name = "inventory_name_snapshot", nullable = false, length = 160)
    private String inventoryNameSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_snapshot", nullable = false, length = 20)
    private InventoryUnit unitSnapshot;

    @Column(name = "ordered_quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal orderedQuantity;

    @Column(name = "received_quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal receivedQuantity = BigDecimal.ZERO.setScale(3);

    @Column(name = "unit_cost_snapshot", nullable = false, precision = 12, scale = 4)
    private BigDecimal unitCostSnapshot;

    @Column(name = "line_total", nullable = false, precision = 14, scale = 4)
    private BigDecimal lineTotal;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PurchaseOrderItemEntity() {
    }

    public PurchaseOrderItemEntity(
            PurchaseOrderEntity purchaseOrder,
            InventoryItemEntity inventoryItem,
            BigDecimal orderedQuantity,
            BigDecimal unitCostSnapshot,
            BigDecimal lineTotal,
            int displayOrder) {
        this.purchaseOrder = purchaseOrder;
        this.inventoryItem = inventoryItem;
        inventoryCodeSnapshot = inventoryItem.getCode();
        inventoryNameSnapshot = inventoryItem.getName();
        unitSnapshot = inventoryItem.getUnit();
        this.orderedQuantity = orderedQuantity;
        this.unitCostSnapshot = unitCostSnapshot;
        this.lineTotal = lineTotal;
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

    public void updateQuantity(BigDecimal orderedQuantity, BigDecimal lineTotal) {
        this.orderedQuantity = orderedQuantity;
        this.lineTotal = lineTotal;
    }

    public void receive(BigDecimal quantity) {
        receivedQuantity = receivedQuantity.add(quantity);
    }

    public BigDecimal remainingQuantity() {
        return orderedQuantity.subtract(receivedQuantity);
    }

    public Long getId() {
        return id;
    }

    public PurchaseOrderEntity getPurchaseOrder() {
        return purchaseOrder;
    }

    public InventoryItemEntity getInventoryItem() {
        return inventoryItem;
    }

    public String getInventoryCodeSnapshot() {
        return inventoryCodeSnapshot;
    }

    public String getInventoryNameSnapshot() {
        return inventoryNameSnapshot;
    }

    public InventoryUnit getUnitSnapshot() {
        return unitSnapshot;
    }

    public BigDecimal getOrderedQuantity() {
        return orderedQuantity;
    }

    public BigDecimal getReceivedQuantity() {
        return receivedQuantity;
    }

    public BigDecimal getUnitCostSnapshot() {
        return unitCostSnapshot;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }
}
