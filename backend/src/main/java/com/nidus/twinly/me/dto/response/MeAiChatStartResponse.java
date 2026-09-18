package com.nidus.twinly.me.dto.response;

import com.nidus.twinly.me.dto.result.MeAiChatStartResult;

public record MeAiChatStartResponse(
        String message,
        Integer turnIndex,
        Boolean isEnd
) {

    public static MeAiChatStartResponse from(MeAiChatStartResult result) {
        return new MeAiChatStartResponse(result.message(), result.turnIndex(), result.isEnd());
    }
}
