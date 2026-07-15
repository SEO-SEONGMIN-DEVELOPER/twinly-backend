package com.nidus.twinly.chat.repository;

import com.nidus.twinly.chat.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    Optional<ChatRoom> findByMatchId(Long matchId);

    List<ChatRoom> findAllByMatchIdIn(List<Long> matchIds);
}