package com.nidus.twinly.chat.repository;

import com.nidus.twinly.chat.entity.Chat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatRepository extends JpaRepository<Chat, Long> {

    @Query(value = """
        SELECT c.*
        FROM chats c
        INNER JOIN (
            SELECT match_id, MAX(sent_at) AS max_sent_at
            FROM chats
            WHERE match_id IN (:matchIds)
            GROUP BY match_id
        ) latest ON c.match_id = latest.match_id AND c.sent_at = latest.max_sent_at
        """, nativeQuery = true)
    List<Chat> findLatestByMatchIdIn(@Param("matchIds") List<Long> matchIds);

    @Query(value = """
            SELECT match_id AS matchId, COUNT(*) AS count
            FROM chats
            WHERE receiver_user_id = :userId AND match_id IN (:matchIds) AND is_read = false
            GROUP BY match_id
            """, nativeQuery = true)
    List<UnreadCountProjection> countUnreadByMatchIdIn(@Param("userId") Long userId, @Param("matchIds") List<Long> matchIds);

    interface UnreadCountProjection {
        Long getMatchId();
        Long getCount();
    }
}
