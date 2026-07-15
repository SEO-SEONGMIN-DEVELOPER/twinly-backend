package com.nidus.twinly.chat.repository;

import com.nidus.twinly.chat.entity.ChatRoomParticipation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatRoomParticipationRepository extends JpaRepository<ChatRoomParticipation, Long> {

    Optional<ChatRoomParticipation> findByRoomIdAndUserId(Long roomId, Long userId);

    List<ChatRoomParticipation> findAllByRoomIdIn(List<Long> roomIds);
}