package com.nidus.twinly.twinupdate.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum TwinUpdateStatus {
    @JsonProperty("inProgress") IN_PROGRESS,
    @JsonProperty("completed") COMPLETED,
    @JsonProperty("discarded") DISCARDED
}
