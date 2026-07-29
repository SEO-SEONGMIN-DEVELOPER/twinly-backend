package com.nidus.twinly.push.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record PushTokenRegisterRequest(
        @NotNull UUID deviceId,
        @NotBlank @Size(max = 100) String deviceModel,
        @NotBlank @Size(max = 512) String fcmToken
) {
}
