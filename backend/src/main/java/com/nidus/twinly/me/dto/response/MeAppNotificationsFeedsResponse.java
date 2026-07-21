package com.nidus.twinly.me.dto.response;

import com.nidus.twinly.me.dto.result.MeAppNotificationsFeedsResult;

import java.util.List;

public record MeAppNotificationsFeedsResponse(
        Integer unreadCount,
        List<MeAppNotificationsFeedsItemResponse> appNotificationFeeds
) {

    public static MeAppNotificationsFeedsResponse from(MeAppNotificationsFeedsResult result) {
        return new MeAppNotificationsFeedsResponse(
                result.unreadCount(),
                result.appNotificationFeeds().stream().map(MeAppNotificationsFeedsItemResponse::from).toList()
        );
    }
}
