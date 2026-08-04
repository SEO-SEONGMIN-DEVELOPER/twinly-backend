package com.nidus.twinly.push.dto.request;

import com.nidus.twinly.device.domain.DevicePlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record PushTokenRegisterRequest(
        @NotNull UUID deviceId,
        @NotNull DevicePlatform platform,
        @NotBlank @Size(max = 512) String fcmToken
) {
}
