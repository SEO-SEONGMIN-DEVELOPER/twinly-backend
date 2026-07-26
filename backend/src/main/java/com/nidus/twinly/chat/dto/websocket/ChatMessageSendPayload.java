package com.nidus.twinly.chat.dto.websocket;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;

public record ChatMessageSendPayload(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @NotNull Long roomId,
        @NotNull String clientMsgId,
        @NotNull String text
) {
}
