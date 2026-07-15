package com.nidus.twinly.chat.dto.result;

public record ChatRoomMessagesResult(
        Integer unreadCount,
        ChatRoomLastMessageResult lastMessage
) {
}
