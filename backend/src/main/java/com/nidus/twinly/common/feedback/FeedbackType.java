package com.nidus.twinly.common.feedback;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum FeedbackType {
    @JsonProperty("withdrawal") WITHDRAWAL,
    @JsonProperty("suggestion") SUGGESTION
}
