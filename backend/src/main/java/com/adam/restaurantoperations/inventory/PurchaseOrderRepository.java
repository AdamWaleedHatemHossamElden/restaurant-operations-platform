package com.adam.restaurantoperations.inventory;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrderEntity, Long> {
    boolean existsByPurchaseOrderNumber(String number);

    List<PurchaseOrderEntity> findAllByOrderByCreatedAtDescIdDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select purchaseOrder from PurchaseOrderEntity purchaseOrder where purchaseOrder.id = :id")
    Optional<PurchaseOrderEntity> findByIdForUpdate(@Param("id") Long id);
}
