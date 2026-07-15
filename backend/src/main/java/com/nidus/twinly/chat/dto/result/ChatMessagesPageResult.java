package com.nidus.twinly.chat.dto.result;

public record ChatMessagesPageResult(
        Long nextCursor,
        Boolean hasMore
) {
}
