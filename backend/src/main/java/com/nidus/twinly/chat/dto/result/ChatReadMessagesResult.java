package com.nidus.twinly.chat.dto.result;

public record ChatReadMessagesResult(
        Long roomId,
        Long lastMessageId
) {
}
