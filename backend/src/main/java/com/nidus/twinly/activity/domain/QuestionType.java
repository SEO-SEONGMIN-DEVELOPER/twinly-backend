package com.nidus.twinly.activity.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum QuestionType {
    @JsonProperty("promise") PROMISE,
    @JsonProperty("persona") PERSONA
}