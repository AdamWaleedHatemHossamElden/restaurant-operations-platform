package com.adam.restaurantoperations.inventory;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeIngredientRepository extends JpaRepository<RecipeIngredientEntity, Long> {
    List<RecipeIngredientEntity> findByRecipeIdOrderByDisplayOrderAscIdAsc(Long recipeId);

    void deleteByRecipeId(Long recipeId);
}
