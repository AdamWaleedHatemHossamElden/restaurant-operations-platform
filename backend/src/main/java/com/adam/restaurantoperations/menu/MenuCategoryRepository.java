package com.adam.restaurantoperations.menu;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MenuCategoryRepository extends JpaRepository<MenuCategoryEntity, Long>, JpaSpecificationExecutor<MenuCategoryEntity> {
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
