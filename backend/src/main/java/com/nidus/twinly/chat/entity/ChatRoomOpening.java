package com.nidus.twinly.chat.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "chat_room_openings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoomOpening {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_a_id")
    private Long userAId;

    @Column(name = "user_b_id")
    private Long userBId;

    private Instant scheduledAt;

    private Instant openedAt;

    private Instant createdAt;

    public static ChatRoomOpening create(Long userId, Long partnerUserId, Instant scheduledAt) {
        ChatRoomOpening opening = new ChatRoomOpening();
        opening.userAId = Math.min(userId, partnerUserId);
        opening.userBId = Math.max(userId, partnerUserId);
        opening.scheduledAt = scheduledAt;
        opening.createdAt = Instant.now();
        return opening;
    }

    public void markOpened(Instant openedAt) {
        this.openedAt = openedAt;
    }
}
