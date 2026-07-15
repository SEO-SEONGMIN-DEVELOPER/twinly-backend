package com.nidus.twinly.chat.dto.request;

public record ChatSendMessageRequest(
        String text,
        String clientMsgId
) {
}
