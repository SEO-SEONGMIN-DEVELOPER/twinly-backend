package com.nidus.twinly.me.dto.response;

import com.nidus.twinly.me.dto.result.MeAiChatMessageResult;

public record MeAiChatMessageResponse(
        String message,
        Integer turnIndex,
        Boolean isEnd
) {

    public static MeAiChatMessageResponse from(MeAiChatMessageResult result) {
        return new MeAiChatMessageResponse(result.message(), result.turnIndex(), result.isEnd());
    }
}
