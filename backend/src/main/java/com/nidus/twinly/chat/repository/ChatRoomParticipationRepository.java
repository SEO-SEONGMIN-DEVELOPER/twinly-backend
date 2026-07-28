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

    @Modifying
    @Query(value = """
            UPDATE chat_room_participations
            SET last_read_message_id = :lastMessageId
            WHERE room_id = :roomId
              AND user_id = :userId
              AND (last_read_message_id IS NULL OR last_read_message_id < :lastMessageId)
            """, nativeQuery = true)
    int advanceReadPointer(@Param("roomId") Long roomId, @Param("userId") Long userId, @Param("lastMessageId") Long lastMessageId);
}
