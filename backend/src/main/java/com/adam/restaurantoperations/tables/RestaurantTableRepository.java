package com.adam.restaurantoperations.tables;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RestaurantTableRepository
        extends JpaRepository<RestaurantTableEntity, Long>, JpaSpecificationExecutor<RestaurantTableEntity> {

    boolean existsByTableNumberIgnoreCase(String tableNumber);

    boolean existsByTableNumberIgnoreCaseAndIdNot(String tableNumber, Long id);
}
