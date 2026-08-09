package com.adam.restaurantoperations.menu;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

@Entity @Table(name="menu_item_modifier_groups")
public class MenuItemModifierGroupEntity {
    @EmbeddedId private MenuItemModifierGroupId id;
    @MapsId("menuItemId") @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="menu_item_id") private MenuItemEntity menuItem;
    @MapsId("modifierGroupId") @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="modifier_group_id") private ModifierGroupEntity modifierGroup;
    @Column(name="display_order",nullable=false) private int displayOrder;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    protected MenuItemModifierGroupEntity(){}
    public MenuItemModifierGroupEntity(MenuItemEntity item,ModifierGroupEntity group,int order){id=new MenuItemModifierGroupId(item.getId(),group.getId());menuItem=item;modifierGroup=group;displayOrder=order;createdAt=Instant.now();}
    public MenuItemEntity getMenuItem(){return menuItem;} public ModifierGroupEntity getModifierGroup(){return modifierGroup;} public int getDisplayOrder(){return displayOrder;}
}
