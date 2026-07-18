package com.nidus.twinly.chat.repository;

import com.nidus.twinly.chat.entity.Chat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    @Query(value = """
        SELECT c.*
        FROM chats c
        WHERE c.room_id = :roomId
          AND (:cursor IS NULL OR c.id < :cursor)
        ORDER BY c.id DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Chat> findBeforeCursorByRoomId(@Param("roomId") Long roomId, @Param("cursor") Long cursor, @Param("limit") Integer limit);

    @Modifying
    @Query(value = """
        UPDATE chats
        SET is_read = true
        WHERE room_id = :roomId
          AND receiver_user_id = :userId
          AND id <= :lastMessageId
          AND is_read = false
        """, nativeQuery = true)
    int markAsRead(@Param("roomId") Long roomId, @Param("userId") Long userId, @Param("lastMessageId") Long lastMessageId);

    @Query(value = """
        SELECT COUNT(DISTINCT room_id) 
        FROM chats 
        WHERE receiver_user_id = :userId 
            AND is_read = false
        """, nativeQuery = true)
    int countUnreadRoomsByUserId(@Param("userId") Long userId);
}
