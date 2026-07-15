package com.nidus.twinly.chat.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "chat_room_participations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoomParticipation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long matchId;

    private Long userId;

    private Instant entryAgreedAt;

    private Boolean isFavorite;

    private Boolean isHidden;

    private Instant leftAt;

    private Instant createdAt;

    public static ChatRoomParticipation create(Long matchId, Long userId) {
        ChatRoomParticipation participation = new ChatRoomParticipation();

        participation.matchId = matchId;
        participation.userId = userId;
        participation.isFavorite = false;
        participation.isHidden = false;
        participation.createdAt = Instant.now();

        return participation;
    }
}