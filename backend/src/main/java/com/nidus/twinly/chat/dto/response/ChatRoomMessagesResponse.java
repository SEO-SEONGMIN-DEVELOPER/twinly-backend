package com.nidus.twinly.chat.dto.response;

import com.nidus.twinly.chat.dto.result.ChatRoomMessagesResult;

public record ChatRoomMessagesResponse(
        Integer unreadCount,
        ChatRoomLastMessageResponse lastMessage
) {

    public static ChatRoomMessagesResponse from(ChatRoomMessagesResult result) {
        return new ChatRoomMessagesResponse(result.unreadCount(), ChatRoomLastMessageResponse.from(result.lastMessage()));
    }
}
