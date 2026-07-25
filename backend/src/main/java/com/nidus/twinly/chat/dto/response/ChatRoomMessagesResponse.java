package com.nidus.twinly.chat.dto.response;

import com.nidus.twinly.chat.dto.result.ChatRoomMessagesResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record ChatRoomMessagesResponse(
        Integer unreadCount,
        @Schema(nullable = true)
        ChatRoomLastMessageResponse lastMessage
) {

    public static ChatRoomMessagesResponse from(ChatRoomMessagesResult result) {
        return new ChatRoomMessagesResponse(result.unreadCount(), ChatRoomLastMessageResponse.from(result.lastMessage()));
    }
}
