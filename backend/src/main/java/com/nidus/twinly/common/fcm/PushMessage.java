package com.nidus.twinly.common.fcm;

import com.google.firebase.messaging.Message;

public record PushMessage(
        String token,
        Message message
) {
}
