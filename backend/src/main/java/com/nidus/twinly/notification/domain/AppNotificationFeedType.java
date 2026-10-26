package com.nidus.twinly.notification.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum AppNotificationFeedType {
    @JsonProperty("friend")              FRIEND,
    @JsonProperty("match")               MATCH,
    @JsonProperty("twinView")            TWIN_VIEW,
    @JsonProperty("oneTime")             ONE_TIME,
    @JsonProperty("notice")              NOTICE,
    @JsonProperty("balanceGameAnswered") BALANCE_GAME_ANSWERED,
    @JsonProperty("schoolVerification")  SCHOOL_VERIFICATION,
    @JsonProperty("seasonRecap")         SEASON_RECAP
}
