package com.nidus.twinly.chat.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.chat.dto.result.ChatReadMessagesResult;

public record ChatReadMessagesResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long roomId,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long lastMsgId
) {

    public static ChatReadMessagesResponse from(ChatReadMessagesResult result) {
        return new ChatReadMessagesResponse(result.roomId(), result.lastMessageId());
    }
}
