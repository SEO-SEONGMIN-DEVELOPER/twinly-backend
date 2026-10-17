package com.nidus.twinly.notification.dto.command;

import com.nidus.twinly.notification.dto.request.AppNotificationNoticePublishRequest;

import java.util.List;

public record AppNotificationNoticePublishCommand(
        String title,
        String body,
        List<Long> userIds
) {

    public static AppNotificationNoticePublishCommand from(AppNotificationNoticePublishRequest request) {
        return new AppNotificationNoticePublishCommand(request.title(), request.body(), request.userIds());
    }
}
