package com.adam.restaurantoperations.kitchen;

import java.time.Instant;

import com.adam.restaurantoperations.orders.OrderItemEntity;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "kitchen_ticket_items")
public class KitchenTicketItemEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "kitchen_ticket_id", nullable = false)
    private KitchenTicketEntity ticket;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_item_id", nullable = false, unique = true)
    private OrderItemEntity orderItem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private KitchenItemStatus status = KitchenItemStatus.QUEUED;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ready_at")
    private Instant readyAt;

    protected KitchenTicketItemEntity() {
    }

    public KitchenTicketItemEntity(KitchenTicketEntity ticket, OrderItemEntity orderItem) {
        this.ticket = ticket;
        this.orderItem = orderItem;
    }

    @PrePersist
    void initializeTimestamps() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = Instant.now();
    }

    public void transitionTo(KitchenItemStatus target, Instant changedAt) {
        status = target;
        if (target == KitchenItemStatus.PREPARING && startedAt == null) {
            startedAt = changedAt;
        }
        if (target == KitchenItemStatus.READY && readyAt == null) {
            readyAt = changedAt;
        }
    }

    public Long getId() {
        return id;
    }

    public KitchenTicketEntity getTicket() {
        return ticket;
    }

    public OrderItemEntity getOrderItem() {
        return orderItem;
    }

    public KitchenItemStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getReadyAt() {
        return readyAt;
    }
}
