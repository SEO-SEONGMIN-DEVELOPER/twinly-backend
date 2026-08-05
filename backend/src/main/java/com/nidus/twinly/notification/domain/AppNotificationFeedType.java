package com.nidus.twinly.notification.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum AppNotificationFeedType {
    @JsonProperty("friend") FRIEND,
    @JsonProperty("match")  MATCH
}
