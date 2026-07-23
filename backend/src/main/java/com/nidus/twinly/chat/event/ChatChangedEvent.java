package com.nidus.twinly.chat.event;

import java.util.List;

public record ChatChangedEvent(
        Long roomId,
        List<Long> participantUserIds
) {
}
