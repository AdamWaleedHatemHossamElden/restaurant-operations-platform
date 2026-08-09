package com.adam.restaurantoperations.menu;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MenuItemRepository extends JpaRepository<MenuItemEntity, Long>, JpaSpecificationExecutor<MenuItemEntity> {
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select item from MenuItemEntity item where item.id = :id")
    Optional<MenuItemEntity> findByIdForOrderPricing(@Param("id") Long id);
}
