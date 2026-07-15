package com.nidus.twinly.chat.dto.response;

import com.nidus.twinly.chat.dto.result.ChatSendMessageResult;

import java.time.Instant;

public record ChatSendMessageResponse(
        Long messageId,
        String text,
        Instant sentAt,
        String clientMsgId
) {

    public static ChatSendMessageResponse from(ChatSendMessageResult result) {
        return new ChatSendMessageResponse(result.messageId(), result.text(), result.sentAt(), result.clientMsgId());
    }
}
