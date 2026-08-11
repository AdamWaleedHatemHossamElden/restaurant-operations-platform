package com.adam.restaurantoperations.inventory;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockMovementRepository extends JpaRepository<StockMovementEntity, Long> {
    List<StockMovementEntity> findByInventoryItemIdOrderByOccurredAtDescIdDesc(Long inventoryItemId);

    boolean existsBySourceKey(String sourceKey);

    @Query(value = """
            SELECT COALESCE(SUM(CASE
                WHEN movement_type IN ('RECEIPT', 'ADJUSTMENT_IN') THEN quantity
                ELSE -quantity END), 0.000)
            FROM stock_movements
            WHERE inventory_item_id = :itemId
            """, nativeQuery = true)
    BigDecimal balance(@Param("itemId") Long itemId);
}
