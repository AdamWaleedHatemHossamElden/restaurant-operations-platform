package com.adam.restaurantoperations.inventory;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ModifierOptionIngredientRepository
        extends JpaRepository<ModifierOptionIngredientEntity, Long> {
    List<ModifierOptionIngredientEntity> findByModifierOptionIdOrderByDisplayOrderAscIdAsc(Long optionId);

    List<ModifierOptionIngredientEntity> findByModifierOptionIdInOrderByModifierOptionIdAscDisplayOrderAscIdAsc(
            List<Long> optionIds);

    void deleteByModifierOptionId(Long optionId);
}
