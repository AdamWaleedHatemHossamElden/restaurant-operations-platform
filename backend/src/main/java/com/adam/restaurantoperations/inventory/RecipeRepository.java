package com.adam.restaurantoperations.inventory;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecipeRepository extends JpaRepository<RecipeEntity, Long> {
    Optional<RecipeEntity> findByMenuItemId(Long menuItemId);

    List<RecipeEntity> findAllByOrderByMenuItemNameAscIdAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select recipe from RecipeEntity recipe where recipe.menuItem.id = :menuItemId")
    Optional<RecipeEntity> findByMenuItemIdForUpdate(@Param("menuItemId") Long menuItemId);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select recipe from RecipeEntity recipe where recipe.menuItem.id = :menuItemId and recipe.active = true")
    Optional<RecipeEntity> findActiveByMenuItemIdForConsumption(@Param("menuItemId") Long menuItemId);
}
