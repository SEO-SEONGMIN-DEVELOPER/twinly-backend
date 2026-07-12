package com.nidus.twinly.aichat.repository;

import com.nidus.twinly.aichat.domain.AiChatSender;
import com.nidus.twinly.aichat.entity.AiChat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AiChatRepository extends JpaRepository<AiChat, Long> {

    @Query(value = """
            SELECT * FROM ai_chats
            WHERE anon_session_id = :anonSessionId
            ORDER BY turn_index ASC, CASE WHEN sender = 'AI' THEN 0 ELSE 1 END ASC
            """, nativeQuery = true)
    List<AiChat> findByAnonSessionIdOrderByTurnIndexAscSenderDesc(@Param("anonSessionId") Long anonSessionId);

    boolean existsByAnonSessionId(Long anonSessionId);

    Optional<AiChat> findByAnonSessionIdAndTurnIndexAndSender(Long anonSessionId, Integer turnIndex, AiChatSender sender);
}