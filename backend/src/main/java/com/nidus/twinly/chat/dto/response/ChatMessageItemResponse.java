package com.nidus.twinly.chat.dto.response;

import com.nidus.twinly.chat.dto.result.ChatMessageItemResult;
import com.nidus.twinly.common.websocket.domain.ChatSenderType;

import java.time.Instant;

public record ChatMessageItemResponse(
        Long messageId,
        ChatSenderType senderType,
        String text,
        Instant sentAt
) {

    public static ChatMessageItemResponse from(ChatMessageItemResult result) {
        return new ChatMessageItemResponse(result.messageId(), result.senderType(), result.text(), result.sentAt());
    }
}
