package com.adam.restaurantoperations.orders;

import java.math.BigDecimal;
import java.time.Instant;

import com.adam.restaurantoperations.menu.ModifierGroupEntity;
import com.adam.restaurantoperations.menu.ModifierOptionEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_item_modifiers")
public class OrderItemModifierEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItemEntity orderItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "modifier_group_id", nullable = false)
    private ModifierGroupEntity modifierGroup;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "modifier_option_id", nullable = false)
    private ModifierOptionEntity modifierOption;

    @Column(name = "group_name_snapshot", nullable = false, length = 120)
    private String groupNameSnapshot;

    @Column(name = "option_name_snapshot", nullable = false, length = 120)
    private String optionNameSnapshot;

    @Column(name = "price_adjustment_snapshot", nullable = false, precision = 12, scale = 2)
    private BigDecimal priceAdjustmentSnapshot;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OrderItemModifierEntity() {
    }

    public OrderItemModifierEntity(
            OrderItemEntity orderItem,
            ModifierGroupEntity modifierGroup,
            ModifierOptionEntity modifierOption,
            String groupNameSnapshot,
            String optionNameSnapshot,
            BigDecimal priceAdjustmentSnapshot,
            int displayOrder) {
        this.orderItem = orderItem;
        this.modifierGroup = modifierGroup;
        this.modifierOption = modifierOption;
        this.groupNameSnapshot = groupNameSnapshot;
        this.optionNameSnapshot = optionNameSnapshot;
        this.priceAdjustmentSnapshot = priceAdjustmentSnapshot;
        this.displayOrder = displayOrder;
    }

    @PrePersist
    void initializeTimestamp() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public OrderItemEntity getOrderItem() {
        return orderItem;
    }

    public ModifierGroupEntity getModifierGroup() {
        return modifierGroup;
    }

    public ModifierOptionEntity getModifierOption() {
        return modifierOption;
    }

    public String getGroupNameSnapshot() {
        return groupNameSnapshot;
    }

    public String getOptionNameSnapshot() {
        return optionNameSnapshot;
    }

    public BigDecimal getPriceAdjustmentSnapshot() {
        return priceAdjustmentSnapshot;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }
}
