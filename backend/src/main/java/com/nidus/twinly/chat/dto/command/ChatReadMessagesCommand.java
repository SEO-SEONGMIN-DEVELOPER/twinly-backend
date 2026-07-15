package com.nidus.twinly.chat.dto.command;

import com.nidus.twinly.chat.dto.request.ChatReadMessagesRequest;

public record ChatReadMessagesCommand(
        Long lastMessageId
) {

    public static ChatReadMessagesCommand from(ChatReadMessagesRequest request) {
        return new ChatReadMessagesCommand(request.lastMessageId());
    }
}
