package com.nidus.twinly.chat.dto.websocket;


import com.fasterxml.jackson.annotation.JsonFormat;

public record ChatReadRejectedPayload(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long roomId,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long lastMsgId,
        CommandError error
) {
}
