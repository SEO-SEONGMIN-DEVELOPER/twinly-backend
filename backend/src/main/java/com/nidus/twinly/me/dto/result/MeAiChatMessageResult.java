package com.nidus.twinly.me.dto.result;

public record MeAiChatMessageResult(
        String message,
        Integer turnIndex,
        Boolean isEnd
) {
}
