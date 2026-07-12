package com.nidus.twinly.common.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum Gender {
    @JsonProperty("male") MALE,
    @JsonProperty("female") FEMALE
}