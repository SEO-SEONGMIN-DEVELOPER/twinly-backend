package com.nidus.twinly.aichat.repository;

import com.nidus.twinly.aichat.domain.AiChatSender;
import com.nidus.twinly.aichat.entity.TwinUpdateAiChat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TwinUpdateAiChatRepository extends JpaRepository<TwinUpdateAiChat, Long> {

    Optional<TwinUpdateAiChat> findByTwinUpdateIdAndTurnIndexAndSender(Long twinUpdateId, Integer turnIndex, AiChatSender sender);

    @Query(value = """
            SELECT * FROM twin_update_ai_chats
            WHERE twin_update_id = :twinUpdateId
            ORDER BY turn_index ASC, CASE WHEN sender = 'AI' THEN 0 ELSE 1 END ASC
            """, nativeQuery = true)
    List<TwinUpdateAiChat> findByTwinUpdateIdOrderByTurnIndexAscSenderDesc(@Param("twinUpdateId") Long twinUpdateId);

    @Query("""
            SELECT c.message FROM TwinUpdateAiChat c, TwinUpdate u
            WHERE c.twinUpdateId = u.id
              AND u.userId = :userId
              AND u.status = com.nidus.twinly.twinupdate.domain.TwinUpdateStatus.COMPLETED
              AND c.sender = com.nidus.twinly.aichat.domain.AiChatSender.AI
            ORDER BY u.completedAt ASC, c.turnIndex ASC
            """)
    List<String> findCompletedQuestionsByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM TwinUpdateAiChat c WHERE c.twinUpdateId = :twinUpdateId")
    void deleteAllByTwinUpdateId(@Param("twinUpdateId") Long twinUpdateId);
}
