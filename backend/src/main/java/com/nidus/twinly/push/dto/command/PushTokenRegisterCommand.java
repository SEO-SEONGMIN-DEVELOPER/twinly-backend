package com.nidus.twinly.push.dto.command;

import com.nidus.twinly.push.dto.request.PushTokenRegisterRequest;

import java.util.UUID;

public record PushTokenRegisterCommand(
        UUID deviceId,
        String deviceModel,
        String fcmToken
) {

    public static PushTokenRegisterCommand from(PushTokenRegisterRequest request) {
        return new PushTokenRegisterCommand(request.deviceId(), request.deviceModel(), request.fcmToken());
    }
}
