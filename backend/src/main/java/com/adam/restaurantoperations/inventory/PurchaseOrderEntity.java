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
import jakarta.persistence.Version;

@Entity
@Table(name = "purchase_orders")
public class PurchaseOrderEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "purchase_order_number", nullable = false, unique = true, length = 32)
    private String purchaseOrderNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private SupplierEntity supplier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PurchaseOrderStatus status = PurchaseOrderStatus.DRAFT;

    @Column(length = 1000)
    private String notes;

    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal subtotal = BigDecimal.ZERO.setScale(4);

    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal total = BigDecimal.ZERO.setScale(4);

    @Column(name = "ordered_at")
    private Instant orderedAt;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected PurchaseOrderEntity() {
    }

    public PurchaseOrderEntity(String purchaseOrderNumber, SupplierEntity supplier, String notes) {
        this.purchaseOrderNumber = purchaseOrderNumber;
        this.supplier = supplier;
        this.notes = notes;
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

    public void updateDraft(SupplierEntity supplier, String notes) {
        this.supplier = supplier;
        this.notes = notes;
    }

    public void updateTotals(BigDecimal value) {
        subtotal = value;
        total = value;
    }

    public void order(Instant now) {
        status = PurchaseOrderStatus.ORDERED;
        orderedAt = now;
    }

    public void receive(boolean complete, Instant now) {
        status = complete ? PurchaseOrderStatus.RECEIVED : PurchaseOrderStatus.PARTIALLY_RECEIVED;
        updatedAt = now;
        if (complete) {
            receivedAt = now;
        }
    }

    public void cancel(Instant now) {
        status = PurchaseOrderStatus.CANCELLED;
        cancelledAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getPurchaseOrderNumber() {
        return purchaseOrderNumber;
    }

    public SupplierEntity getSupplier() {
        return supplier;
    }

    public PurchaseOrderStatus getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public Instant getOrderedAt() {
        return orderedAt;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
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
