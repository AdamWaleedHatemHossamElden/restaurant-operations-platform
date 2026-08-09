package com.adam.restaurantoperations.menu;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MenuItemModifierGroupRepository extends JpaRepository<MenuItemModifierGroupEntity, MenuItemModifierGroupId> {
    @Query("""
            select assignment from MenuItemModifierGroupEntity assignment
            where assignment.menuItem.id = :itemId
            order by assignment.displayOrder, assignment.modifierGroup.id
            """)
    List<MenuItemModifierGroupEntity> findOrderedByMenuItemId(@Param("itemId") Long itemId);

    boolean existsByModifierGroupIdAndMenuItemActiveTrue(Long groupId);
    long countByModifierGroupId(Long groupId);
    void deleteByMenuItemId(Long itemId);
}
