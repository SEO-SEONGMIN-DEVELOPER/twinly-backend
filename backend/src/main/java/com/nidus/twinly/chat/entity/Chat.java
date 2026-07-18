package com.nidus.twinly.chat.entity;

import org.hibernate.annotations.DynamicUpdate;
import com.nidus.twinly.chat.domain.ChatMessageType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@DynamicUpdate
@Table(name = "chats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Chat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String clientMsgId;

    private Long roomId;

    private Long senderUserId;

    private Long receiverUserId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private ChatMessageType type;

    private String message;

    private Instant sentAt;

    private Boolean isRead;

    private Instant createdAt;

    public static Chat create(String clientMsgId, Long roomId, Long senderUserId, Long receiverUserId, ChatMessageType type, String message) {
        Chat chat = new Chat();

        chat.clientMsgId = clientMsgId;
        chat.roomId = roomId;
        chat.senderUserId = senderUserId;
        chat.receiverUserId = receiverUserId;
        chat.type = type;
        chat.message = message;
        chat.sentAt = Instant.now();
        chat.isRead = false;
        chat.createdAt = Instant.now();

        return chat;
    }
}