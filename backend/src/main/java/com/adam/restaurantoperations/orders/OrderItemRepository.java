package com.adam.restaurantoperations.orders;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderItemRepository extends JpaRepository<OrderItemEntity, Long> {
    List<OrderItemEntity> findByOrderIdOrderByDisplayOrderAscIdAsc(Long orderId);

    Optional<OrderItemEntity> findByIdAndOrderId(Long id, Long orderId);

    long countByOrderId(Long orderId);

    @Query("select coalesce(max(item.displayOrder), -1) from OrderItemEntity item where item.order.id = :orderId")
    int maximumDisplayOrder(@Param("orderId") Long orderId);
}
