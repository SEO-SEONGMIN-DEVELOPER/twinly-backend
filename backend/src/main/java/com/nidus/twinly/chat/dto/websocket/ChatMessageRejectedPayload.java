package com.nidus.twinly.chat.dto.websocket;


import com.fasterxml.jackson.annotation.JsonFormat;

public record ChatMessageRejectedPayload(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long roomId,
        String clientMsgId,
        CommandError error
) {
}
