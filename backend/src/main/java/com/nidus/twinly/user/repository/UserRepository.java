package com.nidus.twinly.user.repository;

import com.nidus.twinly.user.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByNickname(String nickname);

    boolean existsByPhoneNumberHash(String phoneNumberHash);

    boolean existsByEmailHash(String emailHash);

    boolean existsByDiHash(String diHash);

    Optional<User> findByPhoneNumberHash(String phoneNumberHash);

    Optional<User> findByEmailHash(String emailHash);

    Optional<User> findByRevenueCatUserId(UUID revenueCatUserId);

    @Modifying
    @Query("UPDATE User u SET u.purchasesSyncedAt = :syncedAt WHERE u.id = :userId")
    void markPurchasesSynced(@Param("userId") Long userId, @Param("syncedAt") Instant syncedAt);

    @Modifying
    @Query("UPDATE User u SET u.poolNumber = :poolNumber WHERE u.id = :userId AND u.poolNumber IS NULL")
    int assignPoolNumber(@Param("userId") Long userId, @Param("poolNumber") int poolNumber);

    List<User> findAllByDeletedAtIsNullAndWithdrawalScheduledAtLessThanEqual(Instant now, Pageable pageable);

    int countByWithdrawalRequestedAtIsNullAndDeletedAtIsNull();

    int countByWithdrawalRequestedAtIsNullAndDeletedAtIsNullAndOrganizationHash(String organizationHash);

    @Query(value = """
            SELECT u.id
            FROM users u
            WHERE u.withdrawal_requested_at IS NULL
              AND u.deleted_at IS NULL
              AND (:cursor IS NULL OR u.id > :cursor)
              AND EXISTS (
                  SELECT 1
                  FROM user_entitlements e
                  WHERE e.user_id = u.id
                    AND e.entitlement = :entitlement
                    AND (e.expires_at IS NULL OR e.expires_at > :now)
              )
              AND :requiredPolicyCount = (
                  SELECT COUNT(DISTINCT a.policy_id)
                  FROM agreements a
                  WHERE a.user_id = u.id
                    AND a.revoked_at IS NULL
                    AND a.policy_id IN (:requiredPolicyIds)
              )
            ORDER BY u.id ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<Long> findIdsAfterCursor(@Param("cursor") Long cursor,
                                  @Param("entitlement") String entitlement,
                                  @Param("now") Instant now,
                                  @Param("requiredPolicyIds") Collection<Long> requiredPolicyIds,
                                  @Param("requiredPolicyCount") int requiredPolicyCount,
                                  @Param("limit") int limit);
}