package com.adam.restaurantoperations.payments;

import java.math.BigDecimal;
import java.time.Instant;

import com.adam.restaurantoperations.orders.OrderEntity;
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
@Table(name = "payments")
public class PaymentEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_number", nullable = false, unique = true, length = 32)
    private String paymentNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderEntity order;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 64)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.SUCCEEDED;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency = "EUR";

    @Column(name = "external_reference", unique = true, length = 120)
    private String externalReference;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private Long actorUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PaymentEntity() {
    }

    public PaymentEntity(
            String paymentNumber,
            OrderEntity order,
            String idempotencyKey,
            PaymentMethod method,
            BigDecimal amount,
            String externalReference,
            Long actorUserId) {
        this.paymentNumber = paymentNumber;
        this.order = order;
        this.idempotencyKey = idempotencyKey;
        this.method = method;
        this.amount = amount;
        this.externalReference = externalReference;
        this.actorUserId = actorUserId;
    }

    @PrePersist
    void initializeTimestamps() {
        receivedAt = Instant.now();
        createdAt = receivedAt;
    }

    public Long getId() { return id; }
    public String getPaymentNumber() { return paymentNumber; }
    public OrderEntity getOrder() { return order; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public PaymentMethod getMethod() { return method; }
    public PaymentStatus getStatus() { return status; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getExternalReference() { return externalReference; }
    public Instant getReceivedAt() { return receivedAt; }
    public Long getActorUserId() { return actorUserId; }
    public Instant getCreatedAt() { return createdAt; }
}
