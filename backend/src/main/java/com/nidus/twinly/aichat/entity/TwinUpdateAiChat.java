package com.nidus.twinly.aichat.entity;

import com.nidus.twinly.aichat.domain.AiChatSender;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;

@Entity
@DynamicUpdate
@Table(name = "twin_update_ai_chats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TwinUpdateAiChat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long twinUpdateId;

    @Enumerated(EnumType.STRING)
    private AiChatSender sender;

    @Column(columnDefinition = "TEXT")
    private String message;

    private Integer turnIndex;

    private Instant createdAt;

    public static TwinUpdateAiChat create(Long twinUpdateId, AiChatSender sender, String message, Integer turnIndex) {
        TwinUpdateAiChat aiChat = new TwinUpdateAiChat();
        aiChat.twinUpdateId = twinUpdateId;
        aiChat.sender = sender;
        aiChat.message = message;
        aiChat.turnIndex = turnIndex;
        aiChat.createdAt = Instant.now();

        return aiChat;
    }
}
