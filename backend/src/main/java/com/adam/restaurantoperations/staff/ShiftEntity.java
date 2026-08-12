package com.adam.restaurantoperations.staff;

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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "shifts")
public class ShiftEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private EmployeeEntity employee;

    @Enumerated(EnumType.STRING)
    @Column(name = "operational_role", nullable = false, length = 20)
    private OperationalRole operationalRole;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ShiftStatus status = ShiftStatus.SCHEDULED;

    @Column(length = 1000)
    private String notes;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected ShiftEntity() {
    }

    public ShiftEntity(
            EmployeeEntity employee,
            OperationalRole operationalRole,
            Instant startAt,
            Instant endAt,
            String notes) {
        this.employee = employee;
        this.operationalRole = operationalRole;
        this.startAt = startAt;
        this.endAt = endAt;
        this.notes = notes;
    }

    @PrePersist
    void initializeTimestamps() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = Instant.now();
    }

    public void update(
            EmployeeEntity employee,
            OperationalRole operationalRole,
            Instant startAt,
            Instant endAt,
            String notes) {
        this.employee = employee;
        this.operationalRole = operationalRole;
        this.startAt = startAt;
        this.endAt = endAt;
        this.notes = notes;
    }

    public void transitionTo(ShiftStatus target) {
        status = target;
        Instant now = Instant.now();
        if (target == ShiftStatus.COMPLETED) {
            completedAt = now;
        } else if (target == ShiftStatus.CANCELLED) {
            cancelledAt = now;
        }
    }

    public Long getId() { return id; }
    public EmployeeEntity getEmployee() { return employee; }
    public OperationalRole getOperationalRole() { return operationalRole; }
    public Instant getStartAt() { return startAt; }
    public Instant getEndAt() { return endAt; }
    public ShiftStatus getStatus() { return status; }
    public String getNotes() { return notes; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
