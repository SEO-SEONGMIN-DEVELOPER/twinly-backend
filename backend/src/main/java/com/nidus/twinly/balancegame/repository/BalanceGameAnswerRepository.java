package com.nidus.twinly.balancegame.repository;

import com.nidus.twinly.balancegame.entity.BalanceGameAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BalanceGameAnswerRepository extends JpaRepository<BalanceGameAnswer, Long> {

    Optional<BalanceGameAnswer> findByRoundIdAndUserIdAndPartnerUserId(Long roundId, Long userId, Long partnerUserId);

    @Query(value = """
            SELECT mine.user_id AS userId, COUNT(*) AS matchedCount
            FROM balance_game_partner_answers mine
            JOIN balance_game_partner_answers theirs
              ON theirs.round_id = mine.round_id
             AND theirs.user_id = mine.partner_user_id
             AND theirs.partner_user_id = mine.user_id
             AND theirs.option_id = mine.option_id
            WHERE mine.round_id = :roundId
            GROUP BY mine.user_id
            """, nativeQuery = true)
    List<MatchedCountProjection> countMatchesByUserInRound(@Param("roundId") Long roundId);

    @Query(value = """
            SELECT COUNT(*) AS comparedCount,
                   CAST(COALESCE(SUM(mine.option_id = theirs.option_id), 0) AS SIGNED) AS matchedCount
            FROM balance_game_partner_answers mine
            JOIN balance_game_partner_answers theirs
              ON theirs.round_id = mine.round_id
             AND theirs.user_id = mine.partner_user_id
             AND theirs.partner_user_id = mine.user_id
            WHERE mine.user_id = :userId
              AND mine.partner_user_id = :partnerUserId
            """, nativeQuery = true)
    MatchRateProjection countMatchRate(@Param("userId") Long userId, @Param("partnerUserId") Long partnerUserId);

    interface MatchRateProjection {
        Long getComparedCount();

        Long getMatchedCount();
    }

    interface MatchedCountProjection {
        Long getUserId();

        Long getMatchedCount();
    }
}
