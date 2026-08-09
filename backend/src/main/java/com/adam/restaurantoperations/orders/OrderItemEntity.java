package com.adam.restaurantoperations.orders;

import java.math.BigDecimal;
import java.time.Instant;

import com.adam.restaurantoperations.menu.MenuItemEntity;
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
@Table(name = "order_items")
public class OrderItemEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderEntity order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menu_item_id", nullable = false)
    private MenuItemEntity menuItem;

    @Column(name = "item_code_snapshot", nullable = false, length = 40)
    private String itemCodeSnapshot;

    @Column(name = "item_name_snapshot", nullable = false, length = 160)
    private String itemNameSnapshot;

    @Column(name = "base_price_snapshot", nullable = false, precision = 12, scale = 2)
    private BigDecimal basePriceSnapshot;

    @Column(nullable = false)
    private int quantity;

    @Column(length = 1000)
    private String notes;

    @Column(name = "unit_total_snapshot", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitTotalSnapshot;

    @Column(name = "line_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OrderItemEntity() {
    }

    public OrderItemEntity(
            OrderEntity order,
            MenuItemEntity menuItem,
            String itemCodeSnapshot,
            String itemNameSnapshot,
            BigDecimal basePriceSnapshot,
            int quantity,
            String notes,
            BigDecimal unitTotalSnapshot,
            BigDecimal lineTotal,
            int displayOrder) {
        this.order = order;
        this.menuItem = menuItem;
        this.itemCodeSnapshot = itemCodeSnapshot;
        this.itemNameSnapshot = itemNameSnapshot;
        this.basePriceSnapshot = basePriceSnapshot;
        this.quantity = quantity;
        this.notes = notes;
        this.unitTotalSnapshot = unitTotalSnapshot;
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

    public void updateWithoutRepricing(int newQuantity, String newNotes, BigDecimal newLineTotal) {
        quantity = newQuantity;
        notes = newNotes;
        lineTotal = newLineTotal;
    }

    public void replaceSnapshot(
            String code,
            String name,
            BigDecimal basePrice,
            int newQuantity,
            String newNotes,
            BigDecimal unitTotal,
            BigDecimal newLineTotal) {
        itemCodeSnapshot = code;
        itemNameSnapshot = name;
        basePriceSnapshot = basePrice;
        quantity = newQuantity;
        notes = newNotes;
        unitTotalSnapshot = unitTotal;
        lineTotal = newLineTotal;
    }

    public Long getId() {
        return id;
    }

    public OrderEntity getOrder() {
        return order;
    }

    public MenuItemEntity getMenuItem() {
        return menuItem;
    }

    public String getItemCodeSnapshot() {
        return itemCodeSnapshot;
    }

    public String getItemNameSnapshot() {
        return itemNameSnapshot;
    }

    public BigDecimal getBasePriceSnapshot() {
        return basePriceSnapshot;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getNotes() {
        return notes;
    }

    public BigDecimal getUnitTotalSnapshot() {
        return unitTotalSnapshot;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
