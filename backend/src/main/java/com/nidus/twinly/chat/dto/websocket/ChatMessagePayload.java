package com.nidus.twinly.chat.dto.websocket;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.chat.domain.ChatSenderType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record ChatMessagePayload(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long messageId,
        ChatSenderType senderType,
        String text,
        Instant sentAt,
        @Schema(nullable = true,
                description = "보낸 사람 본인(senderType=me)에게만 값이 담긴다. senderType이 me가 아니면 null이다.")
        String clientMsgId
) {
}
