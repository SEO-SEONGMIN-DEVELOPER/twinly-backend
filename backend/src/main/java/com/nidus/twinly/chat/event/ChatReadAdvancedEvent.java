package com.nidus.twinly.chat.event;

import java.util.List;

public record ChatReadAdvancedEvent(
        Long roomId,
        Long lastReadMessageId,
        List<Long> targetUserIds
) {
}
