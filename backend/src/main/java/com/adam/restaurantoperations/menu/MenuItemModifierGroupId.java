package com.adam.restaurantoperations.menu;

import java.io.Serializable;
import java.util.Objects;
import jakarta.persistence.Embeddable;

@Embeddable
public class MenuItemModifierGroupId implements Serializable {
    private Long menuItemId; private Long modifierGroupId;
    protected MenuItemModifierGroupId(){} public MenuItemModifierGroupId(Long itemId,Long groupId){menuItemId=itemId;modifierGroupId=groupId;}
    @Override public boolean equals(Object other){return other instanceof MenuItemModifierGroupId id&&Objects.equals(menuItemId,id.menuItemId)&&Objects.equals(modifierGroupId,id.modifierGroupId);}
    @Override public int hashCode(){return Objects.hash(menuItemId,modifierGroupId);}
}
