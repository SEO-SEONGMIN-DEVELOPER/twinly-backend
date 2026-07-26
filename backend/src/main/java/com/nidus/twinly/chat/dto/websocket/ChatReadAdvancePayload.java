package com.nidus.twinly.chat.dto.websocket;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;

public record ChatReadAdvancePayload(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @NotNull Long roomId,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @NotNull Long lastMsgId
) {
}
