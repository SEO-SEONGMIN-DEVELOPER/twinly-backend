package com.nidus.twinly.chat.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.chat.dto.result.ChatMessagesResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record ChatMessagesResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long roomId,
        List<ChatMessageItemResponse> messages,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @Schema(nullable = true, description = "상대가 마지막으로 읽은 메시지 id. 상대가 아직 아무것도 읽지 않았으면 null이다.")
        Long lastReadMessageId,
        ChatMessagesPageResponse page
) {

    public static ChatMessagesResponse from(ChatMessagesResult result) {
        return new ChatMessagesResponse(
                result.roomId(),
                result.messages().stream().map(ChatMessageItemResponse::from).toList(),
                result.lastReadMessageId(),
                ChatMessagesPageResponse.from(result.page())
        );
    }
}
