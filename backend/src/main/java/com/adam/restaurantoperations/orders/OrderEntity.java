package com.adam.restaurantoperations.orders;

import java.math.BigDecimal;
import java.time.Instant;

import com.adam.restaurantoperations.reservations.ReservationEntity;
import com.adam.restaurantoperations.tables.RestaurantTableEntity;
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
@Table(name = "orders")
public class OrderEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", nullable = false, unique = true, length = 32)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_table_id", nullable = false)
    private RestaurantTableEntity restaurantTable;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id")
    private ReservationEntity reservation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.OPEN;

    @Column(length = 2000)
    private String notes;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO.setScale(2);

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total = BigDecimal.ZERO.setScale(2);

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected OrderEntity() {
    }

    public OrderEntity(
            String orderNumber,
            RestaurantTableEntity restaurantTable,
            ReservationEntity reservation,
            String notes) {
        this.orderNumber = orderNumber;
        this.restaurantTable = restaurantTable;
        this.reservation = reservation;
        this.notes = notes;
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

    public void updateMetadata(
            RestaurantTableEntity table,
            ReservationEntity linkedReservation,
            String updatedNotes) {
        restaurantTable = table;
        reservation = linkedReservation;
        notes = updatedNotes;
    }

    public void updateTotals(BigDecimal newSubtotal) {
        subtotal = newSubtotal;
        total = newSubtotal;
    }

    public void touch() {
        updatedAt = Instant.now();
    }

    public void transitionTo(OrderStatus target, Instant changedAt) {
        status = target;
        if (target == OrderStatus.SUBMITTED) {
            submittedAt = changedAt;
        } else if (target == OrderStatus.COMPLETED) {
            completedAt = changedAt;
        } else if (target == OrderStatus.CANCELLED) {
            cancelledAt = changedAt;
        }
    }

    public Long getId() {
        return id;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public RestaurantTableEntity getRestaurantTable() {
        return restaurantTable;
    }

    public ReservationEntity getReservation() {
        return reservation;
    }

    public OrderStatus getStatus() {
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public long getVersion() {
        return version;
    }
}
