package com.adam.restaurantoperations.users;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

    boolean existsByEmail(String email);

    @Query("""
            select distinct u from UserEntity u
            left join fetch u.userRoles ur
            left join fetch ur.role
            where u.email = :email
            """)
    Optional<UserEntity> findWithRolesByEmail(@Param("email") String email);

    @Query("""
            select distinct u from UserEntity u
            left join fetch u.userRoles ur
            left join fetch ur.role
            where u.id = :id
            """)
    Optional<UserEntity> findWithRolesById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserEntity u where u.id = :id")
    Optional<UserEntity> findByIdForUpdate(@Param("id") Long id);
}
