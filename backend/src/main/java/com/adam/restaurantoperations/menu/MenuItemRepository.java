package com.adam.restaurantoperations.menu;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MenuItemRepository extends JpaRepository<MenuItemEntity, Long>, JpaSpecificationExecutor<MenuItemEntity> {
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
}
