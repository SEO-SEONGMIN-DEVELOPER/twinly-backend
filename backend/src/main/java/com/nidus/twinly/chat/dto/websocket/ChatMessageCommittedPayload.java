package com.nidus.twinly.chat.dto.websocket;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;

public record ChatMessageCommittedPayload(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long roomId,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long messageId,
        String clientMsgId,
        String text,
        Instant sentAt
) {
}
