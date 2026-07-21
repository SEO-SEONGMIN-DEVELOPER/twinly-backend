package com.nidus.twinly.me.dto.response;

import com.nidus.twinly.me.dto.result.MePushNotificationsResult;

public record MePushNotificationsResponse(
        MePushNotificationsSettingsResponse pushNotificationSettings
) {

    public static MePushNotificationsResponse from(MePushNotificationsResult result) {
        return new MePushNotificationsResponse(
                MePushNotificationsSettingsResponse.from(result.pushNotificationSettings())
        );
    }
}
