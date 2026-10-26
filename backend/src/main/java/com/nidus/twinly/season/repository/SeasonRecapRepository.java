package com.nidus.twinly.season.repository;

import com.nidus.twinly.season.entity.SeasonRecap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SeasonRecapRepository extends JpaRepository<SeasonRecap, Long> {

    @Query(value = """
            SELECT r.*
            FROM season_recaps r
            WHERE r.user_id = :userId
              AND r.season_id = (
                  SELECT MAX(s.id)
                  FROM seasons s
                  WHERE s.id < :currentSeasonId
              )
            """, nativeQuery = true)
    Optional<SeasonRecap> findServingByUserId(@Param("userId") Long userId,
                                              @Param("currentSeasonId") Long currentSeasonId);

    @Query(value = """
            SELECT r.user_id
            FROM season_recaps r
            JOIN users u ON u.id = r.user_id AND u.withdrawal_requested_at IS NULL AND u.deleted_at IS NULL
            WHERE r.season_id = :seasonId
            ORDER BY r.user_id
            """, nativeQuery = true)
    List<Long> findAllNotifiableUserIdsBySeasonId(@Param("seasonId") Long seasonId);

    @Query(value = """
            SELECT acted.user_id AS userId,
                   CAST(HOUR(acted.acted_at + INTERVAL 9 HOUR) AS SIGNED) AS kstHour,
                   COUNT(*) AS count
            FROM (
                SELECT c.sender_user_id AS user_id, c.sent_at AS acted_at
                FROM chats c
                WHERE c.sent_at >= :from AND c.sent_at < :to
                UNION ALL
                SELECT a.user_id, MIN(a.created_at)
                FROM balance_game_partner_answers a
                WHERE a.created_at >= :from AND a.created_at < :to
                GROUP BY a.user_id, a.round_id
                UNION ALL
                SELECT q.user_id, q.answered_at
                FROM questions q
                WHERE q.type = 'PERSONA'
                  AND q.is_skipped = FALSE
                  AND q.answered_at >= :from AND q.answered_at < :to
                UNION ALL
                SELECT v.viewer_user_id, v.viewed_at
                FROM twin_views v
                WHERE v.viewed_at >= :from AND v.viewed_at < :to
            ) acted
            GROUP BY userId, kstHour
            """, nativeQuery = true)
    List<HourlyActionCountProjection> countActionsByUserIdAndKstHour(@Param("from") Instant from,
                                                                     @Param("to") Instant to);

    @Modifying
    @Query("DELETE FROM SeasonRecap r WHERE r.seasonId < :seasonId")
    int deleteAllBySeasonIdLessThan(@Param("seasonId") Long seasonId);

    @Modifying
    @Query("DELETE FROM SeasonRecap r WHERE r.userId IN :userIds")
    void deleteAllByUserIdIn(@Param("userIds") List<Long> userIds);

    interface HourlyActionCountProjection {
        Long getUserId();

        Long getKstHour();

        Long getCount();
    }
}
