package com.nidus.twinly.invite.repository;

import com.nidus.twinly.invite.domain.InviteRewardMethod;
import com.nidus.twinly.invite.domain.InviteRewardStatus;
import com.nidus.twinly.invite.domain.InviteRewardWaitingReason;
import com.nidus.twinly.invite.entity.InviteReward;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface InviteRewardRepository extends JpaRepository<InviteReward, Long> {

    Optional<InviteReward> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    boolean existsByDiHash(String diHash);

    @Query("SELECT r.id FROM InviteReward r WHERE r.status <> :granted AND r.heldAt IS NULL ORDER BY r.id")
    List<Long> findIdsInProgress(@Param("granted") InviteRewardStatus granted);

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE InviteReward r
            SET r.status = :status, r.waitingReason = :waitingReason, r.productId = :productId, r.updatedAt = :now
            WHERE r.id = :id
              AND r.status IN :fromStatuses
              AND r.targetExpiresAt IS NULL
              AND r.heldAt IS NULL
            """)
    int changeStatus(@Param("id") Long id,
                     @Param("fromStatuses") List<InviteRewardStatus> fromStatuses,
                     @Param("status") InviteRewardStatus status,
                     @Param("waitingReason") InviteRewardWaitingReason waitingReason,
                     @Param("productId") String productId,
                     @Param("now") Instant now);

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE InviteReward r
            SET r.status = :waiting, r.waitingReason = NULL, r.method = :method, r.productId = :productId,
                r.baseExpiresAt = :baseExpiresAt, r.targetExpiresAt = :targetExpiresAt, r.updatedAt = :now
            WHERE r.id = :id
              AND r.status <> :granted
              AND r.targetExpiresAt IS NULL
              AND r.heldAt IS NULL
            """)
    int startGranting(@Param("id") Long id,
                      @Param("method") InviteRewardMethod method,
                      @Param("productId") String productId,
                      @Param("baseExpiresAt") Instant baseExpiresAt,
                      @Param("targetExpiresAt") Instant targetExpiresAt,
                      @Param("waiting") InviteRewardStatus waiting,
                      @Param("granted") InviteRewardStatus granted,
                      @Param("now") Instant now);

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE InviteReward r
            SET r.attemptCount = r.attemptCount + 1, r.attemptedAt = :now, r.updatedAt = :now
            WHERE r.id = :id
              AND r.attemptCount = :expectedAttemptCount
              AND r.status <> :granted
              AND r.heldAt IS NULL
            """)
    int markAttempted(@Param("id") Long id,
                      @Param("expectedAttemptCount") int expectedAttemptCount,
                      @Param("granted") InviteRewardStatus granted,
                      @Param("now") Instant now);

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE InviteReward r
            SET r.status = :granted, r.waitingReason = NULL, r.method = :method, r.endsAt = :endsAt,
                r.grantedAt = :now, r.updatedAt = :now
            WHERE r.id = :id
              AND r.status <> :granted
            """)
    int markGranted(@Param("id") Long id,
                    @Param("method") InviteRewardMethod method,
                    @Param("endsAt") Instant endsAt,
                    @Param("granted") InviteRewardStatus granted,
                    @Param("now") Instant now);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE InviteReward r SET r.heldAt = :now, r.updatedAt = :now WHERE r.id = :id AND r.heldAt IS NULL")
    int hold(@Param("id") Long id, @Param("now") Instant now);

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE InviteReward r
            SET r.status = :accepted, r.appleTransactionId = :transactionId, r.appleOfferAcceptedAt = :now, r.updatedAt = :now
            WHERE r.id = :id
              AND r.status = :pending
            """)
    int acceptAppleOffer(@Param("id") Long id,
                         @Param("transactionId") String transactionId,
                         @Param("pending") InviteRewardStatus pending,
                         @Param("accepted") InviteRewardStatus accepted,
                         @Param("now") Instant now);

    @Modifying
    @Query(value = """
            UPDATE invite_rewards r
            JOIN users u ON u.id = r.user_id
            SET r.di_hash = NULL
            WHERE r.di_hash IS NOT NULL
              AND u.deleted_at IS NOT NULL
              AND COALESCE(u.withdrawal_requested_at, u.deleted_at) < :cutoff
            """, nativeQuery = true)
    int clearDiHashOfWithdrawnBefore(@Param("cutoff") Instant cutoff);
}
