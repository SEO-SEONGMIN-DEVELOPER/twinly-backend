package com.nidus.twinly.schoolverification.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum SchoolVerificationMethod {
    @JsonProperty("email")     EMAIL,
    @JsonProperty("studentId") STUDENT_ID
}
