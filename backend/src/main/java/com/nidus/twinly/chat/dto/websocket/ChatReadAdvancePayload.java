package com.nidus.twinly.chat.dto.websocket;

import jakarta.validation.constraints.NotNull;

public record ChatReadAdvancePayload(
        @NotNull Long roomId,
        @NotNull Long lastMsgId
) {
}
