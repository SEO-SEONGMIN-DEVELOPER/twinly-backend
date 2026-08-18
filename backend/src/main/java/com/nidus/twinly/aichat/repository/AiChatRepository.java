package com.nidus.twinly.aichat.repository;

import com.nidus.twinly.aichat.entity.AiChat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiChatRepository extends JpaRepository<AiChat, Long> {

    List<AiChat> findAllByUserId(Long userId);
}
