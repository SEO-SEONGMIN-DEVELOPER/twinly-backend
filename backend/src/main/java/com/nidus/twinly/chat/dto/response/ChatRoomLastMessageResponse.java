package com.nidus.twinly.chat.dto.response;

import com.nidus.twinly.chat.dto.result.ChatRoomLastMessageResult;

import java.time.Instant;

public record ChatRoomLastMessageResponse(
        String text,
        Instant sentAt
) {

    public static ChatRoomLastMessageResponse from(ChatRoomLastMessageResult result) {
        return new ChatRoomLastMessageResponse(result.text(), result.sentAt());
    }
}
