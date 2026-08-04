package com.nidus.twinly.me.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum HesitationStatus {
    @JsonProperty("answered")   ANSWERED,
    @JsonProperty("unanswered") UNANSWERED,
    @JsonProperty("all")        ALL
}
