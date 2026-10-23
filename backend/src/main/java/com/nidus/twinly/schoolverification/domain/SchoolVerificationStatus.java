package com.nidus.twinly.schoolverification.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum SchoolVerificationStatus {
    @JsonProperty("approved") APPROVED,
    @JsonProperty("pending")  PENDING,
    @JsonProperty("rejected") REJECTED
}
