package com.nidus.twinly.chat.repository;

import com.nidus.twinly.chat.entity.ChatRoomParticipation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatRoomParticipationRepository extends JpaRepository<ChatRoomParticipation, Long> {

    Optional<ChatRoomParticipation> findByMatchIdAndUserId(Long matchId, Long userId);

    List<ChatRoomParticipation> findAllByMatchIdIn(List<Long> matchIds);
}