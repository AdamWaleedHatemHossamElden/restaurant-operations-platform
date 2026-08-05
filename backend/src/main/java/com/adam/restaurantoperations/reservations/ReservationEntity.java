package com.adam.restaurantoperations.reservations;

import java.time.Instant;

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
@Table(name = "reservations")
public class ReservationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reservation_code", nullable = false, unique = true, length = 24)
    private String reservationCode;

    @Column(name = "guest_name", nullable = false, length = 160)
    private String guestName;

    @Column(name = "guest_phone", nullable = false, length = 32)
    private String guestPhone;

    @Column(name = "guest_email", length = 320)
    private String guestEmail;

    @Column(name = "party_size", nullable = false)
    private int partySize;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_table_id")
    private RestaurantTableEntity restaurantTable;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ReservationStatus status;

    @Column(length = 2000)
    private String notes;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected ReservationEntity() {
    }

    public ReservationEntity(
            String reservationCode,
            String guestName,
            String guestPhone,
            String guestEmail,
            int partySize,
            Instant startAt,
            int durationMinutes,
            RestaurantTableEntity restaurantTable,
            String notes) {
        this.reservationCode = reservationCode;
        this.guestName = guestName;
        this.guestPhone = guestPhone;
        this.guestEmail = guestEmail;
        this.partySize = partySize;
        this.startAt = startAt;
        this.durationMinutes = durationMinutes;
        this.restaurantTable = restaurantTable;
        this.status = ReservationStatus.PENDING;
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

    public void update(
            String guestName,
            String guestPhone,
            String guestEmail,
            int partySize,
            Instant startAt,
            int durationMinutes,
            RestaurantTableEntity restaurantTable,
            String notes) {
        this.guestName = guestName;
        this.guestPhone = guestPhone;
        this.guestEmail = guestEmail;
        this.partySize = partySize;
        this.startAt = startAt;
        this.durationMinutes = durationMinutes;
        this.restaurantTable = restaurantTable;
        this.notes = notes;
    }

    public void transitionTo(ReservationStatus target) {
        status = target;
    }

    public Long getId() {
        return id;
    }

    public String getReservationCode() {
        return reservationCode;
    }

    public String getGuestName() {
        return guestName;
    }

    public String getGuestPhone() {
        return guestPhone;
    }

    public String getGuestEmail() {
        return guestEmail;
    }

    public int getPartySize() {
        return partySize;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public RestaurantTableEntity getRestaurantTable() {
        return restaurantTable;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
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
