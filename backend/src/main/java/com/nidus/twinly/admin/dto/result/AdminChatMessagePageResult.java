package com.nidus.twinly.admin.dto.result;

import java.time.Instant;
import java.util.List;

public record AdminChatMessagePageResult(
        List<Message> messages,
        boolean hasMore
) {

    public record Message(
            Long id,
            Long senderUserId,
            String type,
            String text,
            Instant sentAt
    ) {
    }
}
