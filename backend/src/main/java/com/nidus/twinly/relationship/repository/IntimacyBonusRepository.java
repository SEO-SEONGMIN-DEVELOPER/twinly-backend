package com.nidus.twinly.relationship.repository;

import com.nidus.twinly.relationship.entity.IntimacyBonus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface IntimacyBonusRepository extends JpaRepository<IntimacyBonus, Long> {

    @Query(value = """
            SELECT r.id AS relationshipId,
                   CAST(SUM(CASE WHEN r.intimacy_as_of IS NULL OR b.created_at > r.intimacy_as_of THEN b.amount ELSE 0 END) AS SIGNED) AS amountAfterAsOf,
                   CAST(SUM(b.amount) AS SIGNED) AS totalAmount
            FROM relationships r
            JOIN intimacy_bonuses b
              ON b.user_a_id = LEAST(r.user_id, r.partner_user_id)
             AND b.user_b_id = GREATEST(r.user_id, r.partner_user_id)
             AND b.created_at <= :until
             AND b.season_id <=> r.season_id
            WHERE r.id IN (:relationshipIds)
            GROUP BY r.id
            """, nativeQuery = true)
    List<BonusSumProjection> sumByRelationshipIdIn(@Param("relationshipIds") List<Long> relationshipIds,
                                                   @Param("until") Instant until);

    @Modifying
    @Query("UPDATE IntimacyBonus b SET b.seasonId = :seasonId WHERE b.seasonId IS NULL AND b.createdAt >= :from AND b.createdAt < :to")
    void fillSeasonId(@Param("seasonId") Long seasonId, @Param("from") Instant from, @Param("to") Instant to);

    @Modifying
    @Query("UPDATE IntimacyBonus b SET b.seasonId = NULL WHERE b.seasonId = :seasonId")
    void clearSeasonId(@Param("seasonId") Long seasonId);

    List<IntimacyBonus> findAllByUserAIdAndUserBId(Long userAId, Long userBId);

    interface BonusSumProjection {
        Long getRelationshipId();

        Long getAmountAfterAsOf();

        Long getTotalAmount();
    }
}
