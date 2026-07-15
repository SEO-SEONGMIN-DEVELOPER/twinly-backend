package com.nidus.twinly.chat.dto.response;

import com.nidus.twinly.chat.dto.result.ChatRoomEntryStatusResult;

public record ChatRoomEntryStatusResponse(
        Boolean myEntryAgreed,
        Boolean partnerEntryAgreed
) {

    public static ChatRoomEntryStatusResponse from(ChatRoomEntryStatusResult result) {
        return new ChatRoomEntryStatusResponse(result.myEntryAgreed(), result.partnerEntryAgreed());
    }
}
