package com.nidus.twinly.season.repository;

import com.nidus.twinly.season.entity.SeasonParticipation;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    @Query(value = """
            SELECT p.user_id
            FROM season_participations p
            JOIN users u ON u.id = p.user_id AND u.withdrawal_requested_at IS NULL AND u.deleted_at IS NULL
            WHERE p.season_id = :seasonId
              AND p.user_id <> :userId
              AND u.is_seed = FALSE
              AND p.participated_in_at < :participatedBefore
              AND EXISTS (
                  SELECT 1 FROM scenes s
                  WHERE s.user_id = p.user_id
                    AND s.date BETWEEN :fromDate AND :toDate
                    AND s.ends_at > :endsAfter
              )
              AND NOT EXISTS (
                  SELECT 1 FROM blocks b
                  WHERE (b.user_id = :userId AND b.blocked_user_id = p.user_id)
                     OR (b.user_id = p.user_id AND b.blocked_user_id = :userId)
              )
            """, nativeQuery = true)
    List<Long> findAllMapCandidateUserIds(@Param("userId") Long userId,
                                          @Param("seasonId") Long seasonId,
                                          @Param("participatedBefore") Instant participatedBefore,
                                          @Param("fromDate") LocalDate fromDate,
                                          @Param("toDate") LocalDate toDate,
                                          @Param("endsAfter") LocalDateTime endsAfter);
}
