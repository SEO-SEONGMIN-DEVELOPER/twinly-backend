package com.nidus.twinly.me.dto.result;

public record MeAiChatStartResult(
        String message,
        Integer turnIndex,
        Boolean isEnd
) {
}
