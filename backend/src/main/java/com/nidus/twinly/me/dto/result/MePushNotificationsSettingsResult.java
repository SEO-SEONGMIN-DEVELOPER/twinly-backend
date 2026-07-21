package com.nidus.twinly.me.dto.result;

public record MePushNotificationsSettingsResult(
        Boolean event,
        Boolean chat,
        Boolean marketing
) {
}
