package com.adam.restaurantoperations.tables;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface RestaurantTableRepository
        extends JpaRepository<RestaurantTableEntity, Long>, JpaSpecificationExecutor<RestaurantTableEntity> {

    boolean existsByTableNumberIgnoreCase(String tableNumber);

    boolean existsByTableNumberIgnoreCaseAndIdNot(String tableNumber, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select table from RestaurantTableEntity table where table.id = :id")
    Optional<RestaurantTableEntity> findByIdForReservationUpdate(@Param("id") Long id);
}
