package com.nidus.twinly.push.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record PushTokenRevokeRequest(
        @NotNull UUID deviceId
) {
}
