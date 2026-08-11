package com.adam.restaurantoperations.inventory;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseOrderItemRepository extends JpaRepository<PurchaseOrderItemEntity, Long> {
    List<PurchaseOrderItemEntity> findByPurchaseOrderIdOrderByDisplayOrderAscIdAsc(Long purchaseOrderId);

    Optional<PurchaseOrderItemEntity> findByIdAndPurchaseOrderId(Long id, Long purchaseOrderId);

    boolean existsByPurchaseOrderIdAndInventoryItemId(Long purchaseOrderId, Long inventoryItemId);

    long countByPurchaseOrderId(Long purchaseOrderId);

    @Query("select coalesce(max(item.displayOrder), -1) from PurchaseOrderItemEntity item where item.purchaseOrder.id = :id")
    int maximumDisplayOrder(@Param("id") Long purchaseOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select item from PurchaseOrderItemEntity item
            where item.id = :itemId and item.purchaseOrder.id = :purchaseOrderId
            """)
    Optional<PurchaseOrderItemEntity> findByIdAndOrderIdForUpdate(
            @Param("itemId") Long itemId,
            @Param("purchaseOrderId") Long purchaseOrderId);
}
