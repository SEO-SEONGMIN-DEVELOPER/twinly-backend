package com.nidus.twinly.chat.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.chat.dto.result.ChatMessagesPageResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record ChatMessagesPageResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @Schema(nullable = true)
        Long nextCursor,
        Boolean hasMore
) {

    public static ChatMessagesPageResponse from(ChatMessagesPageResult result) {
        return new ChatMessagesPageResponse(result.nextCursor(), result.hasMore());
    }
}
