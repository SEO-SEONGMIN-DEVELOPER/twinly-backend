package com.nidus.twinly.chat.dto.response;

import com.nidus.twinly.chat.dto.result.ChatMessagesPageResult;

public record ChatMessagesPageResponse(
        Long nextCursor,
        Boolean hasMore
) {

    public static ChatMessagesPageResponse from(ChatMessagesPageResult result) {
        return new ChatMessagesPageResponse(result.nextCursor(), result.hasMore());
    }
}
