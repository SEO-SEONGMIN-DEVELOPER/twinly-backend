package com.nidus.twinly.notification.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum AppNotificationFeedTargetType {
    @JsonProperty("profile") PROFILE,
    @JsonProperty("chat")    CHAT
}
