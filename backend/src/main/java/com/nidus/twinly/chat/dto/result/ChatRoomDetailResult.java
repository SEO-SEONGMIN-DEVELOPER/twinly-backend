package com.nidus.twinly.chat.dto.result;

import java.time.Instant;

public record ChatRoomDetailResult(
        Long roomId,
        Long matchId,
        ChatRoomEntryStatusResult entryStatus,
        ChatRoomDetailPartnerResult partner,
        Instant closedAt,
        String closeReason,
        Boolean isCurrentSeason
) {
}
