package com.nidus.twinly.chat.dto.websocket;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.nidus.twinly.chat.domain.ChatSenderType;
import com.nidus.twinly.common.websocket.serializer.KstInstantSerializer;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatMessagePayload(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long messageId,
        ChatSenderType senderType,
        String text,
        @JsonSerialize(using = KstInstantSerializer.class)
        Instant sentAt,
        String clientMsgId
) {
}
