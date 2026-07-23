package com.nidus.twinly.chat.dto.websocket;

import jakarta.validation.constraints.NotNull;

public record ChatMessageSendPayload(
        @NotNull Long roomId,
        @NotNull String clientMsgId,
        @NotNull String text
) {
}
