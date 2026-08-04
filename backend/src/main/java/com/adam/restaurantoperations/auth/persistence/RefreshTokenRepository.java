package com.adam.restaurantoperations.auth.persistence;

import java.time.Instant;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, Long> {

    @Query("select token.user.id from RefreshTokenEntity token where token.tokenHash = :hash")
    Optional<Long> findUserIdByTokenHash(@Param("hash") String hash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select token from RefreshTokenEntity token
            join fetch token.user
            where token.tokenHash = :hash
            """)
    Optional<RefreshTokenEntity> findByTokenHashForUpdate(@Param("hash") String hash);

    @Modifying
    @Query("""
            update RefreshTokenEntity token
            set token.revokedAt = :revokedAt
            where token.familyId = :familyId and token.revokedAt is null
            """)
    int revokeActiveFamily(
            @Param("familyId") String familyId,
            @Param("revokedAt") Instant revokedAt);

}
