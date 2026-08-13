package com.adam.restaurantoperations.payments;

import java.math.BigDecimal;
import java.time.Instant;

import com.adam.restaurantoperations.orders.OrderItemEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "invoice_items")
public class InvoiceItemEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private InvoiceEntity invoice;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_order_item_id", nullable = false)
    private OrderItemEntity sourceOrderItem;

    @Column(name = "item_code", nullable = false, length = 40)
    private String itemCode;

    @Column(name = "item_name", nullable = false, length = 160)
    private String itemName;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "base_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal basePrice;

    @Column(name = "unit_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitTotal;

    @Column(name = "line_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected InvoiceItemEntity() {
    }

    public InvoiceItemEntity(InvoiceEntity invoice, OrderItemEntity source) {
        this.invoice = invoice;
        this.sourceOrderItem = source;
        this.itemCode = source.getItemCodeSnapshot();
        this.itemName = source.getItemNameSnapshot();
        this.quantity = source.getQuantity();
        this.basePrice = source.getBasePriceSnapshot();
        this.unitTotal = source.getUnitTotalSnapshot();
        this.lineTotal = source.getLineTotal();
        this.displayOrder = source.getDisplayOrder();
    }

    @PrePersist
    void initializeTimestamp() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public InvoiceEntity getInvoice() { return invoice; }
    public OrderItemEntity getSourceOrderItem() { return sourceOrderItem; }
    public String getItemCode() { return itemCode; }
    public String getItemName() { return itemName; }
    public int getQuantity() { return quantity; }
    public BigDecimal getBasePrice() { return basePrice; }
    public BigDecimal getUnitTotal() { return unitTotal; }
    public BigDecimal getLineTotal() { return lineTotal; }
    public int getDisplayOrder() { return displayOrder; }
    public Instant getCreatedAt() { return createdAt; }
}
