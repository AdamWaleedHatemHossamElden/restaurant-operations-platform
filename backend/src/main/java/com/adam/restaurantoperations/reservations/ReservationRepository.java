package com.adam.restaurantoperations.reservations;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository
        extends JpaRepository<ReservationEntity, Long>, JpaSpecificationExecutor<ReservationEntity> {

    boolean existsByReservationCode(String reservationCode);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select reservation from ReservationEntity reservation where reservation.id = :id")
    Optional<ReservationEntity> findByIdForOrderLink(@Param("id") Long id);

    @Query(value = """
            SELECT id
            FROM reservations
            WHERE restaurant_table_id = :tableId
              AND status IN ('CONFIRMED', 'SEATED')
              AND (:excludeId IS NULL OR id <> :excludeId)
              AND start_at < :endAt
              AND TIMESTAMPADD(MINUTE, duration_minutes, start_at) > :startAt
            FOR UPDATE
            """, nativeQuery = true)
    List<Long> findBlockingOverlapIdsForUpdate(
            @Param("tableId") Long tableId,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt,
            @Param("excludeId") Long excludeId);

    @Query(value = """
            SELECT COUNT(*)
            FROM reservations
            WHERE restaurant_table_id = :tableId
              AND status IN ('CONFIRMED', 'SEATED')
              AND (:excludeId IS NULL OR id <> :excludeId)
              AND start_at < :endAt
              AND TIMESTAMPADD(MINUTE, duration_minutes, start_at) > :startAt
            """, nativeQuery = true)
    long countBlockingOverlaps(
            @Param("tableId") Long tableId,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt,
            @Param("excludeId") Long excludeId);
}
