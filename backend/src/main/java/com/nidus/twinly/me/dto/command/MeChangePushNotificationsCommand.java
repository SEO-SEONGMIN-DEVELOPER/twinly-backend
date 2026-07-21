package com.nidus.twinly.me.dto.command;

import com.nidus.twinly.me.dto.request.MeChangePushNotificationsRequest;

public record MeChangePushNotificationsCommand(
        Boolean isEnabled
) {

    public static MeChangePushNotificationsCommand from(MeChangePushNotificationsRequest request) {
        return new MeChangePushNotificationsCommand(request.isEnabled());
    }
}
