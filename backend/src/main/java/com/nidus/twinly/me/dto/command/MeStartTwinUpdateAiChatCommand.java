package com.nidus.twinly.me.dto.command;

import com.nidus.twinly.me.dto.request.MeStartTwinUpdateAiChatRequest;

import java.util.UUID;

public record MeStartTwinUpdateAiChatCommand(
        UUID updateId
) {

    public static MeStartTwinUpdateAiChatCommand from(MeStartTwinUpdateAiChatRequest request) {
        return new MeStartTwinUpdateAiChatCommand(request.updateId());
    }
}
