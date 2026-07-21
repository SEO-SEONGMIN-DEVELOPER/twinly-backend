package com.nidus.twinly.push.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record PushTokenRegisterRequest(
        @NotNull UUID deviceId,
        @NotNull String deviceModel,
        @NotNull String fcmToken
) {
}
