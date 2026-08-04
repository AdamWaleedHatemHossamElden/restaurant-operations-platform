package com.adam.restaurantoperations.tables;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "restaurant_tables")
public class RestaurantTableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "table_number", nullable = false, unique = true, length = 32)
    private String tableNumber;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false, length = 80)
    private String section;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TableStatus status;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected RestaurantTableEntity() {
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

    public RestaurantTableEntity(
            String tableNumber,
            String displayName,
            int capacity,
            String section,
            TableStatus status) {
        this.tableNumber = tableNumber;
        this.displayName = displayName;
        this.capacity = capacity;
        this.section = section;
        this.status = status;
    }

    public void update(
            String tableNumber,
            String displayName,
            int capacity,
            String section,
            TableStatus status) {
        this.tableNumber = tableNumber;
        this.displayName = displayName;
        this.capacity = capacity;
        this.section = section;
        this.status = status;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public String getTableNumber() {
        return tableNumber;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getCapacity() {
        return capacity;
    }

    public String getSection() {
        return section;
    }

    public TableStatus getStatus() {
        return status;
    }

    public boolean isActive() {
        return active;
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
