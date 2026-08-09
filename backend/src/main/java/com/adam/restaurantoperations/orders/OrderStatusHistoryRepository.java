package com.adam.restaurantoperations.orders;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistoryEntity, Long> {
    List<OrderStatusHistoryEntity> findByOrderIdOrderByChangedAtAscIdAsc(Long orderId);
}
