package com.nidus.twinly.chat.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.chat.dto.result.ChatRoomDetailResult;

import java.time.Instant;

public record ChatRoomDetailResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long roomId,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long matchId,
        ChatRoomEntryStatusResponse entryStatus,
        ChatRoomDetailPartnerResponse partner,
        Instant closedAt,
        String closeReason,
        Boolean isCurrentSeason
) {

    public static ChatRoomDetailResponse from(ChatRoomDetailResult result) {
        return new ChatRoomDetailResponse(
                result.roomId(),
                result.matchId(),
                ChatRoomEntryStatusResponse.from(result.entryStatus()),
                ChatRoomDetailPartnerResponse.from(result.partner()),
                result.closedAt(),
                result.closeReason(),
                result.isCurrentSeason()
        );
    }
}
