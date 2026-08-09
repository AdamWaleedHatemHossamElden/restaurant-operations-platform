package com.adam.restaurantoperations.menu;

import java.math.BigDecimal;
import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity @Table(name="modifier_options")
public class ModifierOptionEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="modifier_group_id",nullable=false) private ModifierGroupEntity group;
    @Column(nullable=false,length=120) private String name;
    @Column(name="price_adjustment",nullable=false,precision=12,scale=2) private BigDecimal priceAdjustment;
    @Column(name="display_order",nullable=false) private int displayOrder;
    @Column(nullable=false) private boolean active=true;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @Version @Column(nullable=false) private long version;
    protected ModifierOptionEntity(){}
    public ModifierOptionEntity(ModifierGroupEntity group,String name,BigDecimal price,int order){this.group=group;this.name=name;this.priceAdjustment=price;this.displayOrder=order;}
    @PrePersist void createTimestamps(){createdAt=updatedAt=Instant.now();} @PreUpdate void updateTimestamp(){updatedAt=Instant.now();}
    public void update(String name,BigDecimal price,int order){this.name=name;this.priceAdjustment=price;this.displayOrder=order;} public void setActive(boolean value){active=value;}
    public Long getId(){return id;} public ModifierGroupEntity getGroup(){return group;} public String getName(){return name;} public BigDecimal getPriceAdjustment(){return priceAdjustment;}
    public int getDisplayOrder(){return displayOrder;} public boolean isActive(){return active;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;} public long getVersion(){return version;}
}
