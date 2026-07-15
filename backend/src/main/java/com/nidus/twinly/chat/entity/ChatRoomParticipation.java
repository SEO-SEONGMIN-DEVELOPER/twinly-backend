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

    private Long roomId;

    private Long userId;

    private Instant entryAgreedAt;

    private Boolean isFavorite;

    private Boolean isHidden;

    private Instant leftAt;

    private Instant createdAt;

    public static ChatRoomParticipation create(Long roomId, Long userId) {
        ChatRoomParticipation participation = new ChatRoomParticipation();

        participation.roomId = roomId;
        participation.userId = userId;
        participation.isFavorite = false;
        participation.isHidden = false;
        participation.createdAt = Instant.now();

        return participation;
    }

    public void agree() {
        if (this.entryAgreedAt == null) {
            this.entryAgreedAt = Instant.now();
        }
    }

    public void hide() {
        this.isHidden = true;
    }

    public void leave() {
        this.leftAt = Instant.now();
    }
}