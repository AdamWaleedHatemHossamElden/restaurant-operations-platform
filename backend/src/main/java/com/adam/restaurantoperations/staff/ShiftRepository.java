package com.adam.restaurantoperations.staff;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShiftRepository
        extends JpaRepository<ShiftEntity, Long>, JpaSpecificationExecutor<ShiftEntity> {
    @Query("select shift.employee.id from ShiftEntity shift where shift.id = :id")
    Optional<Long> findEmployeeId(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select shift from ShiftEntity shift where shift.id = :id")
    Optional<ShiftEntity> findByIdForUpdate(@Param("id") Long id);

    @Query(value = """
            SELECT id
            FROM shifts
            WHERE employee_id = :employeeId
              AND status <> 'CANCELLED'
              AND (:excludeId IS NULL OR id <> :excludeId)
              AND start_at < :endAt
              AND end_at > :startAt
            FOR UPDATE
            """, nativeQuery = true)
    List<Long> findBlockingOverlapIdsForUpdate(
            @Param("employeeId") Long employeeId,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt,
            @Param("excludeId") Long excludeId);

    @Query(value = """
            SELECT COUNT(*)
            FROM shifts
            WHERE employee_id = :employeeId
              AND status = 'SCHEDULED'
              AND end_at > :now
            """, nativeQuery = true)
    long countFutureScheduled(@Param("employeeId") Long employeeId, @Param("now") Instant now);
}
