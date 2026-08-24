package com.nidus.twinly.common.fcm;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nidus.twinly.notification.domain.AppNotificationFeedType;

public enum PushType {
    @JsonProperty("friend")      FRIEND,
    @JsonProperty("match")       MATCH,
    @JsonProperty("chatMessage") CHAT_MESSAGE,
    @JsonProperty("twinView")    TWIN_VIEW;

    public static PushType from(AppNotificationFeedType feedType) {
        return switch (feedType) {
            case FRIEND -> FRIEND;
            case MATCH -> MATCH;
            case TWIN_VIEW -> TWIN_VIEW;
        };
    }
}
