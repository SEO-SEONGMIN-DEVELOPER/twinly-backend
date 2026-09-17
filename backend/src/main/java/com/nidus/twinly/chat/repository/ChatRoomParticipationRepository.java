package com.nidus.twinly.chat.repository;

import com.nidus.twinly.chat.entity.ChatRoomParticipation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRoomParticipationRepository extends JpaRepository<ChatRoomParticipation, Long> {

    Optional<ChatRoomParticipation> findByRoomIdAndUserId(Long roomId, Long userId);

    List<ChatRoomParticipation> findAllByRoomId(Long roomId);

    List<ChatRoomParticipation> findAllByRoomIdIn(List<Long> roomIds);

    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE chat_room_participations
            SET last_read_message_id = :lastMessageId
            WHERE room_id = :roomId
              AND user_id = :userId
              AND (last_read_message_id IS NULL OR last_read_message_id < :lastMessageId)
            """, nativeQuery = true)
    int advanceReadPointer(@Param("roomId") Long roomId, @Param("userId") Long userId, @Param("lastMessageId") Long lastMessageId);

    @Modifying
    @Query(value = """
            DELETE p
            FROM chat_room_participations p
            JOIN chat_rooms r ON r.id = p.room_id
            JOIN matches m ON m.id = r.match_id
            WHERE m.user_a_id IN (:userIds)
              AND m.user_b_id IN (:userIds)
            """, nativeQuery = true)
    void deleteAllByRoomBetweenUserIdsIn(@Param("userIds") List<Long> userIds);
}
