package com.nidus.twinly.chat.repository;

import com.nidus.twinly.chat.entity.ChatRoomOpening;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

public interface ChatRoomOpeningRepository extends JpaRepository<ChatRoomOpening, Long> {

    List<ChatRoomOpening> findAllByOpenedAtIsNullAndScheduledAtLessThanEqualOrderByScheduledAtAsc(Instant now);

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO chat_room_openings (user_a_id, user_b_id, scheduled_at, created_at)
            VALUES (:userAId, :userBId, :scheduledAt, UTC_TIMESTAMP(6)) AS new
            ON DUPLICATE KEY UPDATE scheduled_at = LEAST(chat_room_openings.scheduled_at, new.scheduled_at)
            """, nativeQuery = true)
    void upsert(@Param("userAId") Long userAId,
                @Param("userBId") Long userBId,
                @Param("scheduledAt") Instant scheduledAt);
}
