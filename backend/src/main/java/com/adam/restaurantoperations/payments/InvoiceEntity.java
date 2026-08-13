package com.adam.restaurantoperations.payments;

import java.math.BigDecimal;
import java.time.Instant;

import com.adam.restaurantoperations.orders.OrderEntity;
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
@Table(name = "invoices")
public class InvoiceEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_number", nullable = false, unique = true, length = 32)
    private String invoiceNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private OrderEntity order;

    @Column(name = "order_number_snapshot", nullable = false, length = 32)
    private String orderNumberSnapshot;

    @Column(nullable = false, length = 3)
    private String currency = "EUR";

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Column(name = "paid_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal paidTotal;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private Long actorUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected InvoiceEntity() {
    }

    public InvoiceEntity(
            String invoiceNumber,
            OrderEntity order,
            BigDecimal subtotal,
            BigDecimal total,
            BigDecimal paidTotal,
            Long actorUserId) {
        this.invoiceNumber = invoiceNumber;
        this.order = order;
        this.orderNumberSnapshot = order.getOrderNumber();
        this.subtotal = subtotal;
        this.total = total;
        this.paidTotal = paidTotal;
        this.actorUserId = actorUserId;
    }

    @PrePersist
    void initializeTimestamps() {
        issuedAt = Instant.now();
        createdAt = issuedAt;
    }

    public Long getId() { return id; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public OrderEntity getOrder() { return order; }
    public String getOrderNumberSnapshot() { return orderNumberSnapshot; }
    public String getCurrency() { return currency; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getTotal() { return total; }
    public BigDecimal getPaidTotal() { return paidTotal; }
    public Instant getIssuedAt() { return issuedAt; }
    public Long getActorUserId() { return actorUserId; }
    public Instant getCreatedAt() { return createdAt; }
}
