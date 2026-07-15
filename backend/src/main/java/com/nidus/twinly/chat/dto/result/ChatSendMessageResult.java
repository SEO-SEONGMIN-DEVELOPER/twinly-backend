package com.nidus.twinly.chat.dto.result;

import java.time.Instant;

public record ChatSendMessageResult(
        Long messageId,
        String text,
        Instant sentAt,
        String clientMsgId
) {
}
