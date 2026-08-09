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

@Entity
@Table(name = "menu_items")
public class MenuItemEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="category_id",nullable=false) private MenuCategoryEntity category;
    @Column(nullable=false,unique=true,length=40) private String code;
    @Column(nullable=false,length=160) private String name;
    @Column(length=2000) private String description;
    @Column(name="base_price",nullable=false,precision=12,scale=2) private BigDecimal basePrice;
    @Column(name="display_order",nullable=false) private int displayOrder;
    @Column(nullable=false) private boolean active=true;
    @Column(name="available_for_sale",nullable=false) private boolean availableForSale=true;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @Version @Column(nullable=false) private long version;
    protected MenuItemEntity(){}
    public MenuItemEntity(MenuCategoryEntity category,String code,String name,String description,BigDecimal basePrice,int displayOrder){this.category=category;this.code=code;this.name=name;this.description=description;this.basePrice=basePrice;this.displayOrder=displayOrder;}
    @PrePersist void createTimestamps(){createdAt=updatedAt=Instant.now();} @PreUpdate void updateTimestamp(){updatedAt=Instant.now();}
    public void update(MenuCategoryEntity category,String code,String name,String description,BigDecimal basePrice,int displayOrder){this.category=category;this.code=code;this.name=name;this.description=description;this.basePrice=basePrice;this.displayOrder=displayOrder;}
    public void setActive(boolean value){active=value;} public void setAvailableForSale(boolean value){availableForSale=value;} public void touch(){updatedAt=Instant.now();}
    public Long getId(){return id;} public MenuCategoryEntity getCategory(){return category;} public String getCode(){return code;} public String getName(){return name;}
    public String getDescription(){return description;} public BigDecimal getBasePrice(){return basePrice;} public int getDisplayOrder(){return displayOrder;}
    public boolean isActive(){return active;} public boolean isAvailableForSale(){return availableForSale;} public boolean isEffectivelyAvailable(){return category.isActive()&&active&&availableForSale;}
    public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;} public long getVersion(){return version;}
}
