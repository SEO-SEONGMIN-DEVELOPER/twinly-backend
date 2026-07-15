package com.nidus.twinly.common.websocket.dto;

import com.nidus.twinly.chat.entity.Chat;
import com.nidus.twinly.common.websocket.domain.WebSocketEventType;

public record ChatMessageReceivedEvent(
        WebSocketEventType type,
        Long roomId,
        ChatMessagePayload message
) {

    public static ChatMessageReceivedEvent from(Chat chat) {
        return new ChatMessageReceivedEvent(WebSocketEventType.CHAT_MSG, chat.getMatchId(), ChatMessagePayload.from(chat));
    }
}