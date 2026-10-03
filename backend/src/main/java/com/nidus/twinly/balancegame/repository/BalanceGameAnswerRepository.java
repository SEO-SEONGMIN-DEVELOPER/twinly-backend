package com.nidus.twinly.balancegame.repository;

import com.nidus.twinly.balancegame.entity.BalanceGameAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BalanceGameAnswerRepository extends JpaRepository<BalanceGameAnswer, Long> {

    Optional<BalanceGameAnswer> findByRoundIdAndUserId(Long roundId, Long userId);

    @Query(value = """
            SELECT other.user_id
            FROM balance_game_answers other
            WHERE other.round_id = :roundId
              AND other.option_id = :optionId
              AND other.user_id <> :userId
              AND EXISTS (
                  SELECT 1
                  FROM relationships r
                  WHERE ((r.user_id = :userId AND r.partner_user_id = other.user_id)
                      OR (r.user_id = other.user_id AND r.partner_user_id = :userId))
                    AND r.update_time <= :now
              )
              AND NOT EXISTS (
                  SELECT 1
                  FROM blocks b
                  WHERE (b.user_id = :userId AND b.blocked_user_id = other.user_id)
                     OR (b.user_id = other.user_id AND b.blocked_user_id = :userId)
              )
            """, nativeQuery = true)
    List<Long> findMatchedPartnerUserIds(@Param("roundId") Long roundId,
                                         @Param("optionId") Long optionId,
                                         @Param("userId") Long userId,
                                         @Param("now") LocalDateTime now);

    @Query(value = """
            SELECT mine.user_id AS userId, COUNT(*) AS matchedCount
            FROM balance_game_answers mine
            JOIN balance_game_answers other
              ON other.round_id = mine.round_id
             AND other.option_id = mine.option_id
             AND other.user_id <> mine.user_id
            WHERE mine.round_id = :roundId
              AND EXISTS (
                  SELECT 1
                  FROM relationships r
                  WHERE ((r.user_id = mine.user_id AND r.partner_user_id = other.user_id)
                      OR (r.user_id = other.user_id AND r.partner_user_id = mine.user_id))
                    AND r.update_time <= :now
              )
              AND NOT EXISTS (
                  SELECT 1
                  FROM blocks b
                  WHERE (b.user_id = mine.user_id AND b.blocked_user_id = other.user_id)
                     OR (b.user_id = other.user_id AND b.blocked_user_id = mine.user_id)
              )
            GROUP BY mine.user_id
            """, nativeQuery = true)
    List<MatchedCountProjection> countMatchesByUserInRound(@Param("roundId") Long roundId, @Param("now") LocalDateTime now);

    interface MatchedCountProjection {
        Long getUserId();

        Long getMatchedCount();
    }
}
