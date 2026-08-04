package com.nidus.twinly.push.dto.command;

import com.nidus.twinly.device.domain.DevicePlatform;
import com.nidus.twinly.push.dto.request.PushTokenRegisterRequest;

import java.util.UUID;

public record PushTokenRegisterCommand(
        UUID deviceId,
        DevicePlatform platform,
        String fcmToken
) {

    public static PushTokenRegisterCommand from(PushTokenRegisterRequest request) {
        return new PushTokenRegisterCommand(request.deviceId(), request.platform(), request.fcmToken());
    }
}
