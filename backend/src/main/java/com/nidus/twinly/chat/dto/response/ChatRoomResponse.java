package com.nidus.twinly.chat.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.chat.dto.result.ChatRoomResult;

import java.time.Instant;

public record ChatRoomResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long roomId,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long matchId,
        ChatRoomEntryStatusResponse entryStatus,
        ChatRoomPartnerResponse partner,
        String preview,
        ChatRoomMessagesResponse messages,
        Instant closedAt,
        String closeReason,
        Boolean isCurrentSeason
) {

    public static ChatRoomResponse from(ChatRoomResult result) {
        return new ChatRoomResponse(
                result.roomId(),
                result.matchId(),
                ChatRoomEntryStatusResponse.from(result.entryStatus()),
                ChatRoomPartnerResponse.from(result.partner()),
                result.preview(),
                ChatRoomMessagesResponse.from(result.messages()),
                result.closedAt(),
                result.closeReason(),
                result.isCurrentSeason()
        );
    }
}
