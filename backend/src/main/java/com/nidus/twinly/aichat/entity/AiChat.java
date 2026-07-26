package com.nidus.twinly.aichat.entity;

import org.hibernate.annotations.DynamicUpdate;
import com.nidus.twinly.aichat.domain.AiChatSender;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@DynamicUpdate
@Table(name = "ai_chats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiChat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long anonSessionId;

    @Enumerated(EnumType.STRING)
    private AiChatSender sender;

    @Column(columnDefinition = "TEXT")
    private String message;

    private Integer turnIndex;

    private Instant createdAt;

    public static AiChat create(Long anonSessionId, AiChatSender sender, String message, Integer turnIndex) {
        AiChat aiChat = new AiChat();
        aiChat.anonSessionId = anonSessionId;
        aiChat.sender = sender;
        aiChat.message = message;
        aiChat.turnIndex = turnIndex;
        aiChat.createdAt = Instant.now();
        return aiChat;
    }
}