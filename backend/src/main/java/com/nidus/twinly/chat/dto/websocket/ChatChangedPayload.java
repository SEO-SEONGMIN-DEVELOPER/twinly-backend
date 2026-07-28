package com.nidus.twinly.chat.dto.websocket;

import com.fasterxml.jackson.annotation.JsonFormat;

public record ChatChangedPayload(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long roomId
) {
}
