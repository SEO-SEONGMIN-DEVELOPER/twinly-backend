package com.nidus.twinly.notification.dto.response;

import com.nidus.twinly.notification.dto.result.AppNotificationNoticePublishResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record AppNotificationNoticePublishResponse(
        @Schema(description = "이번 요청으로 새로 발행된 피드 수. 이미 같은 공지를 받은 유저는 세지 않는다.")
        Integer publishedCount
) {

    public static AppNotificationNoticePublishResponse from(AppNotificationNoticePublishResult result) {
        return new AppNotificationNoticePublishResponse(result.publishedCount());
    }
}
