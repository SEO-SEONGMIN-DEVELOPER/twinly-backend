package com.nidus.twinly.me.dto.response;

import com.nidus.twinly.me.dto.result.MeAppNotificationsUnreadCountResult;

public record MeAppNotificationsUnreadCountResponse(
        Integer unreadCount
) {

    public static MeAppNotificationsUnreadCountResponse from(MeAppNotificationsUnreadCountResult result) {
        return new MeAppNotificationsUnreadCountResponse(
                result.unreadCount()
        );
    }
}
