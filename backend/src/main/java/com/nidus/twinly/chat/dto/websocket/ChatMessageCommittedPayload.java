package com.nidus.twinly.chat.dto.websocket;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.nidus.twinly.common.websocket.serializer.KstInstantSerializer;

import java.time.Instant;

public record ChatMessageCommittedPayload(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long roomId,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long messageId,
        String clientMsgId,
        String text,
        @JsonSerialize(using = KstInstantSerializer.class)
        Instant sentAt
) {
}
