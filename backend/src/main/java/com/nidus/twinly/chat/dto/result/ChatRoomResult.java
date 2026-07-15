package com.nidus.twinly.chat.dto.result;

public record ChatRoomResult(
        Long roomId,
        Long matchId,
        ChatRoomEntryStatusResult entryStatus,
        ChatRoomPartnerResult partner,
        String preview,
        ChatRoomMessagesResult messages,
        Boolean isCurrentSeason
) {
}
