package com.nidus.twinly.report.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum ReportReason {
    @JsonProperty("spam")               SPAM,
    @JsonProperty("inappropriatePhoto") INAPPROPRIATE_PHOTO,
    @JsonProperty("fraudSuspected")     FRAUD_SUSPECTED,
    @JsonProperty("harassment")         HARASSMENT,
    @JsonProperty("threat")             THREAT,
    @JsonProperty("other")              OTHER
}
