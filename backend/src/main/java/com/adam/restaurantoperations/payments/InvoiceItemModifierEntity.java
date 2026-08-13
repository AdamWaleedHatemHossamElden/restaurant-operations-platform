package com.adam.restaurantoperations.payments;

import java.math.BigDecimal;
import java.time.Instant;

import com.adam.restaurantoperations.orders.OrderItemModifierEntity;
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
@Table(name = "invoice_item_modifiers")
public class InvoiceItemModifierEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_item_id", nullable = false)
    private InvoiceItemEntity invoiceItem;

    @Column(name = "group_name", nullable = false, length = 120)
    private String groupName;

    @Column(name = "option_name", nullable = false, length = 120)
    private String optionName;

    @Column(name = "price_adjustment", nullable = false, precision = 12, scale = 2)
    private BigDecimal priceAdjustment;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected InvoiceItemModifierEntity() {
    }

    public InvoiceItemModifierEntity(InvoiceItemEntity invoiceItem, OrderItemModifierEntity source) {
        this.invoiceItem = invoiceItem;
        this.groupName = source.getGroupNameSnapshot();
        this.optionName = source.getOptionNameSnapshot();
        this.priceAdjustment = source.getPriceAdjustmentSnapshot();
        this.displayOrder = source.getDisplayOrder();
    }

    @PrePersist
    void initializeTimestamp() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public InvoiceItemEntity getInvoiceItem() { return invoiceItem; }
    public String getGroupName() { return groupName; }
    public String getOptionName() { return optionName; }
    public BigDecimal getPriceAdjustment() { return priceAdjustment; }
    public int getDisplayOrder() { return displayOrder; }
    public Instant getCreatedAt() { return createdAt; }
}
