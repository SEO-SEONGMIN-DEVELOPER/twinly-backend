package com.nidus.twinly.common.fcm;

import java.time.Instant;

public record ChatMessagePushContent(
        Long messageId,
        Long roomId,
        Long senderId,
        String senderName,
        String text,
        String senderThumbnailKey,
        Instant createdAt
) {
}
