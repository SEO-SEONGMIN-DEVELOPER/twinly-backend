package com.nidus.twinly.push.dto.command;

import com.nidus.twinly.push.dto.request.PushTokenRevokeRequest;

import java.util.UUID;

public record PushTokenRevokeCommand(
        UUID deviceId
) {

    public static PushTokenRevokeCommand from(PushTokenRevokeRequest request) {
        return new PushTokenRevokeCommand(request.deviceId());
    }
}
