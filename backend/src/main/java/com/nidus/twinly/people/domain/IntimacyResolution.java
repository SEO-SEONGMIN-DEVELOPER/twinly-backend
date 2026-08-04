package com.nidus.twinly.people.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum IntimacyResolution {
    @JsonProperty("day")  DAY,
    @JsonProperty("week") WEEK
}
