package com.nidus.twinly.me.dto.command;

import com.nidus.twinly.me.dto.request.MeSendTwinUpdateAiChatMessageRequest;

import java.util.UUID;

public record MeSendTwinUpdateAiChatMessageCommand(
        UUID updateId,
        String message,
        Integer turnIndex
) {

    public static MeSendTwinUpdateAiChatMessageCommand from(MeSendTwinUpdateAiChatMessageRequest request) {
        return new MeSendTwinUpdateAiChatMessageCommand(
                request.updateId(),
                request.message(),
                request.turnIndex()
        );
    }
}
