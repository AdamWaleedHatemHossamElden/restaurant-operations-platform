package com.adam.restaurantoperations.menu;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ModifierOptionRepository extends JpaRepository<ModifierOptionEntity, Long> {
    List<ModifierOptionEntity> findByGroupIdOrderByDisplayOrderAscNameAsc(Long groupId);
    long countByGroupIdAndActiveTrue(Long groupId);
    boolean existsByGroupIdAndNameIgnoreCase(Long groupId, String name);
    boolean existsByGroupIdAndNameIgnoreCaseAndIdNot(Long groupId, String name, Long id);

    @Query("select option.group.id from ModifierOptionEntity option where option.id = :id")
    Optional<Long> findGroupIdById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select option from ModifierOptionEntity option where option.id = :id")
    Optional<ModifierOptionEntity> findByIdForOrderPricing(@Param("id") Long id);
}
