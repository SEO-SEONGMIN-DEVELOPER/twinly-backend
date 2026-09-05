package com.nidus.twinly.common.fcm;

import com.google.firebase.messaging.Message;

public record PushMessage(
        Long userId,
        PushType type,
        String token,
        Message message
) {
}
