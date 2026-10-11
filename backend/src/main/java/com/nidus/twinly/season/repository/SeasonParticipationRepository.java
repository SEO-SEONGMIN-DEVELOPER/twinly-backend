package com.nidus.twinly.season.repository;

import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.season.entity.SeasonParticipation;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SeasonParticipationRepository extends JpaRepository<SeasonParticipation, Long> {

    boolean existsByUserIdAndSeasonId(Long userId, Long seasonId);

    Optional<SeasonParticipation> findByUserIdAndSeasonId(Long userId, Long seasonId);

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO season_participations (user_id, season_id, participated_in_at, created_at)
            VALUES (:userId, :seasonId, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
            ON DUPLICATE KEY UPDATE id = id
            """, nativeQuery = true)
    void upsert(@Param("userId") Long userId, @Param("seasonId") Long seasonId);

    @Query("""
            SELECT u.id AS userId, u.gender AS gender
            FROM SeasonParticipation p
            JOIN User u ON u.id = p.userId
            WHERE p.seasonId = :seasonId
              AND p.userId <> :userId
              AND p.participatedInAt < :participatedBefore
              AND u.withdrawalRequestedAt IS NULL
              AND u.deletedAt IS NULL
              AND NOT EXISTS (
                  SELECT 1 FROM Relationship r
                  WHERE r.userId = :userId
                    AND r.partnerUserId = p.userId
              )
              AND NOT EXISTS (
                  SELECT 1 FROM Block b
                  WHERE (b.userId = :userId AND b.blockedUserId = p.userId)
                     OR (b.userId = p.userId AND b.blockedUserId = :userId)
              )
            """)
    List<PreviewCandidateProjection> findAllPreviewCandidates(@Param("userId") Long userId,
                                                              @Param("seasonId") Long seasonId,
                                                              @Param("participatedBefore") Instant participatedBefore);

    interface PreviewCandidateProjection {
        Long getUserId();
        Gender getGender();
    }
}
