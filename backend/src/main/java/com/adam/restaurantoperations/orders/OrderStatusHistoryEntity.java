package com.adam.restaurantoperations.orders;

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
import jakarta.persistence.Table;

@Entity
@Table(name = "order_status_history")
public class OrderStatusHistoryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderEntity order;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private OrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private OrderStatus toStatus;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    @Column(name = "changed_by_user_id")
    private Long changedByUserId;

    protected OrderStatusHistoryEntity() {
    }

    public OrderStatusHistoryEntity(
            OrderEntity order,
            OrderStatus fromStatus,
            OrderStatus toStatus,
            Long changedByUserId) {
        this.order = order;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedByUserId = changedByUserId;
    }

    @PrePersist
    void initializeTimestamp() {
        changedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public OrderStatus getFromStatus() {
        return fromStatus;
    }

    public OrderStatus getToStatus() {
        return toStatus;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public Long getChangedByUserId() {
        return changedByUserId;
    }
}
