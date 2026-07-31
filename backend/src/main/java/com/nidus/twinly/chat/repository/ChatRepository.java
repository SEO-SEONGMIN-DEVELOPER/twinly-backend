package com.nidus.twinly.chat.repository;

import com.nidus.twinly.chat.entity.Chat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRepository extends JpaRepository<Chat, Long> {

    Optional<Chat> findBySenderUserIdAndClientMsgId(Long senderUserId, String clientMsgId);

    @Query(value = """
        SELECT c.*
        FROM chats c
        INNER JOIN (
            SELECT room_id, MAX(id) AS max_id
            FROM chats
            WHERE room_id IN (:roomIds)
            GROUP BY room_id
        ) latest ON c.id = latest.max_id
        """, nativeQuery = true)
    List<Chat> findLatestByRoomIdIn(@Param("roomIds") List<Long> roomIds);

    @Query(value = """
            SELECT c.room_id AS roomId, COUNT(*) AS count
            FROM chats c
            JOIN chat_room_participations p ON p.room_id = c.room_id AND p.user_id = :userId
            WHERE c.receiver_user_id = :userId
              AND c.room_id IN (:roomIds)
              AND (p.last_read_message_id IS NULL OR c.id > p.last_read_message_id)
            GROUP BY c.room_id
            """, nativeQuery = true)
    List<UnreadCountProjection> countUnreadByRoomIdIn(@Param("userId") Long userId, @Param("roomIds") List<Long> roomIds);

    interface UnreadCountProjection {
        Long getRoomId();
        Long getCount();
    }

    @Query(value = """
        SELECT c.*
        FROM chats c
        WHERE c.room_id = :roomId
          AND (:cursor IS NULL OR c.id < :cursor)
        ORDER BY c.id DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Chat> findBeforeCursorByRoomId(@Param("roomId") Long roomId, @Param("cursor") Long cursor, @Param("limit") Integer limit);

    boolean existsByIdAndRoomId(Long id, Long roomId);

    @Query(value = """
        SELECT COUNT(DISTINCT c.room_id)
        FROM chats c
        JOIN chat_room_participations p ON p.room_id = c.room_id AND p.user_id = :userId
        WHERE c.receiver_user_id = :userId
          AND (p.last_read_message_id IS NULL OR c.id > p.last_read_message_id)
          AND p.left_at IS NULL
          AND p.is_hidden = FALSE
          AND NOT EXISTS (
              SELECT 1
              FROM blocks b
              WHERE b.user_id = :userId AND b.blocked_user_id = c.sender_user_id
          )
        """, nativeQuery = true)
    int countUnreadRoomsByUserId(@Param("userId") Long userId);
}
