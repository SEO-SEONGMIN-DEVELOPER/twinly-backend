package com.nidus.twinly.push.dto.request;

import java.util.UUID;

public record PushTokenRegisterRequest(
        UUID deviceId,
        String deviceModel,
        String fcmToken
) {
}
