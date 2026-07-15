package com.nidus.twinly.chat.dto.result;

import java.time.Instant;

public record ChatRoomLastMessageResult(
        String text,
        Instant sentAt
) {
}
