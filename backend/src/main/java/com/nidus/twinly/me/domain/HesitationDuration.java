package com.nidus.twinly.me.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum HesitationDuration {
    @JsonProperty("today") TODAY,
    @JsonProperty("all")   ALL
}
