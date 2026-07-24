package com.nidus.twinly.chat.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.chat.dto.result.ChatMessageItemResult;
import com.nidus.twinly.chat.domain.ChatSenderType;

import java.time.Instant;

public record ChatMessageItemResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long messageId,
        ChatSenderType senderType,
        String text,
        Instant sentAt,
        String clientMsgId
) {

    public static ChatMessageItemResponse from(ChatMessageItemResult result) {
        return new ChatMessageItemResponse(result.messageId(), result.senderType(), result.text(), result.sentAt(), result.clientMsgId());
    }
}
