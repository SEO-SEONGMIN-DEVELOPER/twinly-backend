package com.nidus.twinly.chat.dto.result;

import java.time.Instant;

public record ChatRoomResult(
        Long roomId,
        Long matchId,
        ChatRoomEntryStatusResult entryStatus,
        ChatRoomPartnerResult partner,
        String preview,
        ChatRoomMessagesResult messages,
        Instant closedAt,
        String closeReason,
        Boolean isCurrentSeason
) {
}
