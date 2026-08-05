package com.nidus.twinly.notification.event;

import com.nidus.twinly.notification.entity.AppNotificationFeed;

import java.util.List;

public record AppNotificationFeedCreatedEvent(
        List<AppNotificationFeed> feeds
) {
}
