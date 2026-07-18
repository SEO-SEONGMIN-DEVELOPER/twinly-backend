package com.nidus.twinly.main.dto.response;

import com.nidus.twinly.main.dto.result.MainTabResult;

public record MainTabResponse(
        MainTabSeasonResponse season,
        Integer unreadChatRoomCount,
        Integer unreadNotificationCount
) {

    public static MainTabResponse from(MainTabResult result) {
        return new MainTabResponse(
                MainTabSeasonResponse.from(result.season()),
                result.unreadChatRoomCount(),
                result.unreadNotificationCount()
        );
    }
}
