package com.adam.restaurantoperations.orders;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<OrderEntity, Long>, JpaSpecificationExecutor<OrderEntity> {
    boolean existsByOrderNumber(String orderNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select restaurantOrder from OrderEntity restaurantOrder where restaurantOrder.id = :id")
    Optional<OrderEntity> findByIdForUpdate(@Param("id") Long id);
}
