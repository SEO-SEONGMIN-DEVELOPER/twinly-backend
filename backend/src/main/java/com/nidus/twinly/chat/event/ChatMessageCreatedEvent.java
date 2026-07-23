package com.nidus.twinly.chat.event;

import com.nidus.twinly.chat.entity.Chat;

import java.util.List;

public record ChatMessageCreatedEvent(
        Chat chat,
        List<Long> participantUserIds
) {
}
