package com.nidus.twinly.me.dto.command;

import com.nidus.twinly.me.dto.request.MeAppNotificationsReadAllRequest;

public record MeAppNotificationsReadAllCommand(
        Long lastAppNotificationId
) {

    public static MeAppNotificationsReadAllCommand from(MeAppNotificationsReadAllRequest request) {
        return new MeAppNotificationsReadAllCommand(request.lastAppNotificationId());
    }
}
