package com.nidus.twinly.chat.dto.command;

import com.nidus.twinly.chat.dto.request.ChatSendMessageRequest;

public record ChatSendMessageCommand(
        String text,
        String clientMsgId
) {

    public static ChatSendMessageCommand from(ChatSendMessageRequest request) {
        return new ChatSendMessageCommand(request.text(), request.clientMsgId());
    }
}