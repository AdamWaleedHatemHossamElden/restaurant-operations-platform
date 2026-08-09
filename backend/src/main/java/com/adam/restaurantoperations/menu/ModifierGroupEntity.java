package com.adam.restaurantoperations.menu;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity @Table(name="modifier_groups")
public class ModifierGroupEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,unique=true,length=120) private String name;
    @Column(length=1000) private String description;
    @Enumerated(EnumType.STRING) @Column(name="selection_type",nullable=false,length=20) private SelectionType selectionType;
    @Column(name="minimum_selections",nullable=false) private int minimumSelections;
    @Column(name="maximum_selections",nullable=false) private int maximumSelections;
    @Column(name="display_order",nullable=false) private int displayOrder;
    @Column(nullable=false) private boolean active=true;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @Version @Column(nullable=false) private long version;
    protected ModifierGroupEntity(){}
    public ModifierGroupEntity(String name,String description,SelectionType type,int minimum,int maximum,int order){this.name=name;this.description=description;this.selectionType=type;this.minimumSelections=minimum;this.maximumSelections=maximum;this.displayOrder=order;}
    @PrePersist void createTimestamps(){createdAt=updatedAt=Instant.now();} @PreUpdate void updateTimestamp(){updatedAt=Instant.now();}
    public void update(String name,String description,SelectionType type,int minimum,int maximum,int order){this.name=name;this.description=description;this.selectionType=type;this.minimumSelections=minimum;this.maximumSelections=maximum;this.displayOrder=order;}
    public void setActive(boolean value){active=value;}
    public Long getId(){return id;} public String getName(){return name;} public String getDescription(){return description;} public SelectionType getSelectionType(){return selectionType;}
    public int getMinimumSelections(){return minimumSelections;} public int getMaximumSelections(){return maximumSelections;} public int getDisplayOrder(){return displayOrder;}
    public boolean isActive(){return active;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;} public long getVersion(){return version;}
}
