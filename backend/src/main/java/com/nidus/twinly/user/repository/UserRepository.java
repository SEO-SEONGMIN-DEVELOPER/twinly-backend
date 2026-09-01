package com.nidus.twinly.user.repository;

import com.nidus.twinly.user.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByNickname(String nickname);

    boolean existsByPhoneNumberHash(String phoneNumberHash);

    boolean existsByEmailHash(String emailHash);

    boolean existsByCiHash(String ciHash);

    Optional<User> findByPhoneNumberHash(String phoneNumberHash);

    Optional<User> findByEmailHash(String emailHash);

    Optional<User> findByRevenueCatUserId(UUID revenueCatUserId);

    @Modifying
    @Query("UPDATE User u SET u.purchasesSyncedAt = :syncedAt WHERE u.id = :userId")
    void markPurchasesSynced(@Param("userId") Long userId, @Param("syncedAt") Instant syncedAt);

    List<User> findAllByDeletedAtIsNullAndWithdrawalScheduledAtLessThanEqual(Instant now, Pageable pageable);

    int countByDeletedAtIsNull();

    int countByDeletedAtIsNullAndOrganizationHash(String organizationHash);

    @Query(value = """
            SELECT u.id
            FROM users u
            WHERE u.deleted_at IS NULL
              AND (:cursor IS NULL OR u.id > :cursor)
              AND EXISTS (
                  SELECT 1
                  FROM user_entitlements e
                  WHERE e.user_id = u.id
                    AND e.entitlement = :entitlement
                    AND (e.expires_at IS NULL OR e.expires_at > :now)
              )
            ORDER BY u.id ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<Long> findIdsAfterCursor(@Param("cursor") Long cursor,
                                  @Param("entitlement") String entitlement,
                                  @Param("now") Instant now,
                                  @Param("limit") int limit);
}