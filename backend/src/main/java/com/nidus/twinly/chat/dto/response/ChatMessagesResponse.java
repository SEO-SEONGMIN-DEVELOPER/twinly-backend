package com.nidus.twinly.chat.dto.response;

import com.nidus.twinly.chat.dto.result.ChatMessagesResult;

import java.util.List;

public record ChatMessagesResponse(
        Long roomId,
        List<ChatMessageItemResponse> messages,
        ChatMessagesPageResponse page
) {

    public static ChatMessagesResponse from(ChatMessagesResult result) {
        return new ChatMessagesResponse(
                result.roomId(),
                result.messages().stream().map(ChatMessageItemResponse::from).toList(),
                ChatMessagesPageResponse.from(result.page())
        );
    }
}
