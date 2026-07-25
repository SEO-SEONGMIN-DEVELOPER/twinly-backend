package com.nidus.twinly.chat.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.chat.dto.result.ChatRoomResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record ChatRoomResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long roomId,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long matchId,
        ChatRoomEntryStatusResponse entryStatus,
        ChatRoomPartnerResponse partner,
        @Schema(nullable = true)
        String preview,
        ChatRoomMessagesResponse messages,
        @Schema(nullable = true)
        Instant closedAt,
        @Schema(nullable = true)
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
