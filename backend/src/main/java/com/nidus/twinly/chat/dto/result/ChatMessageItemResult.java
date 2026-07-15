package com.nidus.twinly.chat.dto.result;

import com.nidus.twinly.common.websocket.domain.ChatSenderType;

import java.time.Instant;

public record ChatMessageItemResult(
        Long messageId,
        ChatSenderType senderType,
        String text,
        Instant sentAt
) {
}
