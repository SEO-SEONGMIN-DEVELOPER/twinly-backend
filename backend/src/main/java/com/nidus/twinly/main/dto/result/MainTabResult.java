package com.nidus.twinly.main.dto.result;

public record MainTabResult(
        MainTabSeasonResult season,
        Integer unreadChatRoomCount,
        Integer unreadNotificationCount,
        MainTabLatestRecapResult latestRecap,
        boolean dotWorldEnabled
) {
}
