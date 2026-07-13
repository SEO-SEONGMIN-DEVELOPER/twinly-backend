package com.nidus.twinly.push.dto.request;

import java.util.UUID;

public record PushTokenRevokeRequest(
        UUID deviceId
) {
}
