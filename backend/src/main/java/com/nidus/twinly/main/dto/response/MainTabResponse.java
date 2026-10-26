package com.nidus.twinly.main.dto.response;

import com.nidus.twinly.main.dto.result.MainTabResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record MainTabResponse(
        MainTabSeasonResponse season,
        Integer unreadChatRoomCount,
        Integer unreadNotificationCount,
        @Schema(nullable = true)
        MainTabLatestRecapResponse latestRecap
) {

    public static MainTabResponse from(MainTabResult result) {
        return new MainTabResponse(
                MainTabSeasonResponse.from(result.season()),
                result.unreadChatRoomCount(),
                result.unreadNotificationCount(),
                result.latestRecap() != null ? MainTabLatestRecapResponse.from(result.latestRecap()) : null
        );
    }
}
