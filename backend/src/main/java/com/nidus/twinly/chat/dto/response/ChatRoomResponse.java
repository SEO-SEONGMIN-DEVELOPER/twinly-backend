package com.nidus.twinly.chat.dto.response;

import com.nidus.twinly.chat.dto.result.ChatRoomResult;

public record ChatRoomResponse(
        Long roomId,
        Long matchId,
        ChatRoomEntryStatusResponse entryStatus,
        ChatRoomPartnerResponse partner,
        String preview,
        ChatRoomMessagesResponse messages,
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
                result.isCurrentSeason()
        );
    }
}
