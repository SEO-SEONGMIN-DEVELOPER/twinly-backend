package com.nidus.twinly.chat.entity;

import org.hibernate.annotations.DynamicUpdate;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@DynamicUpdate
@Table(name = "chat_rooms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long matchId;

    private Instant closedAt;

    private String closeReason;

    private Instant createdAt;

    public static ChatRoom create(Long matchId) {
        ChatRoom room = new ChatRoom();

        room.matchId = matchId;
        room.createdAt = Instant.now();

        return room;
    }

    public void close(String reason) {
        this.closedAt = Instant.now();
        this.closeReason = reason;
    }

    public void reopen() {
        this.closedAt = null;
        this.closeReason = null;
    }
}