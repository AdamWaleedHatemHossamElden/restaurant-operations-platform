package com.adam.restaurantoperations.orders;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemModifierRepository extends JpaRepository<OrderItemModifierEntity, Long> {
    List<OrderItemModifierEntity> findByOrderItemIdOrderByDisplayOrderAscIdAsc(Long orderItemId);

    void deleteByOrderItemId(Long orderItemId);
}
