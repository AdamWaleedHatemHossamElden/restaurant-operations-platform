package com.adam.restaurantoperations.staff;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeAvailabilityRepository extends JpaRepository<EmployeeAvailabilityEntity, Long> {
    @Query("""
            select availability
            from EmployeeAvailabilityEntity availability
            where availability.employee.id = :employeeId
              and availability.startAt < :endAt
              and availability.endAt > :startAt
            order by availability.startAt, availability.id
            """)
    List<EmployeeAvailabilityEntity> findInRange(
            @Param("employeeId") Long employeeId,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select availability
            from EmployeeAvailabilityEntity availability
            where availability.employee.id = :employeeId
              and availability.id = :id
            """)
    Optional<EmployeeAvailabilityEntity> findByEmployeeAndIdForUpdate(
            @Param("employeeId") Long employeeId,
            @Param("id") Long id);

    @Query(value = """
            SELECT id
            FROM employee_availability
            WHERE employee_id = :employeeId
              AND (:excludeId IS NULL OR id <> :excludeId)
              AND start_at < :endAt
              AND end_at > :startAt
            FOR UPDATE
            """, nativeQuery = true)
    List<Long> findOverlapIdsForUpdate(
            @Param("employeeId") Long employeeId,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt,
            @Param("excludeId") Long excludeId);

    @Query(value = """
            SELECT id
            FROM employee_availability
            WHERE employee_id = :employeeId
              AND start_at <= :startAt
              AND end_at >= :endAt
            FOR UPDATE
            """, nativeQuery = true)
    List<Long> findCoveringIdsForUpdate(
            @Param("employeeId") Long employeeId,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt);
}
