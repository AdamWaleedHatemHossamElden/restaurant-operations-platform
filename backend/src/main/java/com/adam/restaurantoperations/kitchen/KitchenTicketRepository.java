package com.adam.restaurantoperations.kitchen;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KitchenTicketRepository
        extends JpaRepository<KitchenTicketEntity, Long>, JpaSpecificationExecutor<KitchenTicketEntity> {
    Optional<KitchenTicketEntity> findByOrderId(Long orderId);

    boolean existsByOrderId(Long orderId);

    @Query("select ticket.order.id from KitchenTicketEntity ticket where ticket.id = :id")
    Optional<Long> findOrderIdById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ticket from KitchenTicketEntity ticket where ticket.id = :id")
    Optional<KitchenTicketEntity> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ticket from KitchenTicketEntity ticket where ticket.order.id = :orderId")
    Optional<KitchenTicketEntity> findByOrderIdForUpdate(@Param("orderId") Long orderId);
}
