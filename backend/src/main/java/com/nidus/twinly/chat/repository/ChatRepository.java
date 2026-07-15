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
            SELECT room_id, MAX(sent_at) AS max_sent_at
            FROM chats
            WHERE room_id IN (:roomIds)
            GROUP BY room_id
        ) latest ON c.room_id = latest.room_id AND c.sent_at = latest.max_sent_at
        """, nativeQuery = true)
    List<Chat> findLatestByRoomIdIn(@Param("roomIds") List<Long> roomIds);

    @Query(value = """
            SELECT room_id AS roomId, COUNT(*) AS count
            FROM chats
            WHERE receiver_user_id = :userId AND room_id IN (:roomIds) AND is_read = false
            GROUP BY room_id
            """, nativeQuery = true)
    List<UnreadCountProjection> countUnreadByRoomIdIn(@Param("userId") Long userId, @Param("roomIds") List<Long> roomIds);

    interface UnreadCountProjection {
        Long getRoomId();
        Long getCount();
    }
}
