package com.nidus.twinly.notification.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum NotificationType {
    @JsonProperty("event")     EVENT,
    @JsonProperty("chat")      CHAT,
    @JsonProperty("marketing") MARKETING
}