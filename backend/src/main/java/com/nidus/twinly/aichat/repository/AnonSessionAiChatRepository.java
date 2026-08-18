package com.nidus.twinly.aichat.repository;

import com.nidus.twinly.aichat.domain.AiChatSender;
import com.nidus.twinly.aichat.entity.AnonSessionAiChat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AnonSessionAiChatRepository extends JpaRepository<AnonSessionAiChat, Long> {

    @Query(value = """
            SELECT * FROM anon_session_ai_chats
            WHERE anon_session_id = :anonSessionId
            ORDER BY turn_index ASC, CASE WHEN sender = 'AI' THEN 0 ELSE 1 END ASC
            """, nativeQuery = true)
    List<AnonSessionAiChat> findByAnonSessionIdOrderByTurnIndexAscSenderDesc(@Param("anonSessionId") Long anonSessionId);

    boolean existsByAnonSessionId(Long anonSessionId);

    Optional<AnonSessionAiChat> findByAnonSessionIdAndTurnIndexAndSender(Long anonSessionId, Integer turnIndex, AiChatSender sender);

    List<AnonSessionAiChat> findAllByAnonSessionId(Long anonSessionId);
}