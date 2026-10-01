package com.nidus.twinly.common.fcm;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nidus.twinly.notification.domain.AppNotificationFeedType;

public enum PushType {
    @JsonProperty("friend")          FRIEND,
    @JsonProperty("match")           MATCH,
    @JsonProperty("chatMessage")     CHAT_MESSAGE,
    @JsonProperty("twinView")        TWIN_VIEW,
    @JsonProperty("oneTime")         ONE_TIME,
    @JsonProperty("oneTimePushOnly") ONE_TIME_PUSH_ONLY;

    public static PushType from(AppNotificationFeedType feedType) {
        return switch (feedType) {
            case FRIEND -> FRIEND;
            case MATCH -> MATCH;
            case TWIN_VIEW -> TWIN_VIEW;
            case ONE_TIME -> ONE_TIME;
        };
    }
}
