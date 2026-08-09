package com.adam.restaurantoperations.menu;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "menu_categories")
public class MenuCategoryEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 120) private String name;
    @Column(length = 1000) private String description;
    @Column(name = "display_order", nullable = false) private int displayOrder;
    @Column(nullable = false) private boolean active = true;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private long version;
    protected MenuCategoryEntity() {}
    public MenuCategoryEntity(String name, String description, int displayOrder) { this.name=name; this.description=description; this.displayOrder=displayOrder; }
    @PrePersist void createTimestamps(){createdAt=updatedAt=Instant.now();}
    @PreUpdate void updateTimestamp(){updatedAt=Instant.now();}
    public void update(String name,String description,int displayOrder){this.name=name;this.description=description;this.displayOrder=displayOrder;}
    public void setActive(boolean active){this.active=active;}
    public Long getId(){return id;} public String getName(){return name;} public String getDescription(){return description;}
    public int getDisplayOrder(){return displayOrder;} public boolean isActive(){return active;} public Instant getCreatedAt(){return createdAt;}
    public Instant getUpdatedAt(){return updatedAt;} public long getVersion(){return version;}
}
