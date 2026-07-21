package com.nidus.twinly.me.dto.result;

import java.util.List;

public record MeAppNotificationsFeedsResult(
        Integer unreadCount,
        List<MeAppNotificationsFeedsItemResult> appNotificationFeeds
) {
}
