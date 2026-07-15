package com.nidus.twinly.chat.dto.result;

public record ChatRoomEntryStatusResult(
        Boolean myEntryAgreed,
        Boolean partnerEntryAgreed
) {
}
