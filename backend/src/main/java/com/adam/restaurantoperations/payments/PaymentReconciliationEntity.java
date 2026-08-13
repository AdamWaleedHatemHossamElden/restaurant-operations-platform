package com.adam.restaurantoperations.payments;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "payment_reconciliations")
public class PaymentReconciliationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, unique = true)
    private PaymentEntity payment;

    @Column(name = "reconciliation_reference", length = 120)
    private String reconciliationReference;

    @Column(name = "reconciled_at", nullable = false, updatable = false)
    private Instant reconciledAt;

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private Long actorUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PaymentReconciliationEntity() {
    }

    public PaymentReconciliationEntity(
            PaymentEntity payment,
            String reconciliationReference,
            Long actorUserId) {
        this.payment = payment;
        this.reconciliationReference = reconciliationReference;
        this.actorUserId = actorUserId;
    }

    @PrePersist
    void initializeTimestamps() {
        reconciledAt = Instant.now();
        createdAt = reconciledAt;
    }

    public Long getId() { return id; }
    public PaymentEntity getPayment() { return payment; }
    public String getReconciliationReference() { return reconciliationReference; }
    public Instant getReconciledAt() { return reconciledAt; }
    public Long getActorUserId() { return actorUserId; }
    public Instant getCreatedAt() { return createdAt; }
}
