package com.nidus.twinly.common.websocket.dto;

import com.nidus.twinly.common.websocket.domain.ChatSenderType;
import com.nidus.twinly.chat.entity.Chat;

import java.time.Instant;

public record ChatMessagePayload(
        Long messageId,
        ChatSenderType senderType,
        String text,
        Instant sentAt,
        String clientMsgId
) {

    public static ChatMessagePayload from(Chat chat) {
        return new ChatMessagePayload(chat.getId(), ChatSenderType.THEM, chat.getMessage(), chat.getSentAt(), chat.getClientMsgId());
    }
}