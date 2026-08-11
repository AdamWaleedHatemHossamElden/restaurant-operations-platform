package com.adam.restaurantoperations.inventory;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryItemRepository extends JpaRepository<InventoryItemEntity, Long> {
    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    List<InventoryItemEntity> findAllByOrderByNameAscIdAsc();

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select item from InventoryItemEntity item where item.id = :id")
    Optional<InventoryItemEntity> findByIdForConfiguration(@Param("id") Long id);
}
