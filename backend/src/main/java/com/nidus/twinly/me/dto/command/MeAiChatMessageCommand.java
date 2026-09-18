package com.nidus.twinly.me.dto.command;

import com.nidus.twinly.me.dto.request.MeAiChatMessageRequest;

public record MeAiChatMessageCommand(
        String message,
        Integer turnIndex
) {

    public static MeAiChatMessageCommand from(MeAiChatMessageRequest request) {
        return new MeAiChatMessageCommand(request.message(), request.turnIndex());
    }
}
