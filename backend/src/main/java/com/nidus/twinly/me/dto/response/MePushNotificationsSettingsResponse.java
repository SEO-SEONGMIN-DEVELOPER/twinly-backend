package com.nidus.twinly.me.dto.response;

import com.nidus.twinly.me.dto.result.MePushNotificationsSettingsResult;

public record MePushNotificationsSettingsResponse(
        Boolean event,
        Boolean chat,
        Boolean marketing
) {

    public static MePushNotificationsSettingsResponse from(MePushNotificationsSettingsResult result) {
        return new MePushNotificationsSettingsResponse(result.event(), result.chat(), result.marketing());
    }
}
