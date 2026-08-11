package com.adam.restaurantoperations.inventory;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupplierInventoryItemRepository extends JpaRepository<SupplierInventoryItemEntity, Long> {
    List<SupplierInventoryItemEntity> findBySupplierIdOrderByInventoryItemNameAscIdAsc(Long supplierId);

    Optional<SupplierInventoryItemEntity> findBySupplierIdAndInventoryItemId(Long supplierId, Long itemId);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
            select relationship from SupplierInventoryItemEntity relationship
            where relationship.supplier.id = :supplierId
              and relationship.inventoryItem.id = :itemId
              and relationship.active = true
            """)
    Optional<SupplierInventoryItemEntity> findActiveForPricing(
            @Param("supplierId") Long supplierId,
            @Param("itemId") Long itemId);
}
