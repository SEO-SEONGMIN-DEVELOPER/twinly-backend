package com.nidus.twinly.connection.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum ConnectionDrainingReason {

    @JsonProperty("maintenance") MAINTENANCE,
    @JsonProperty("deploy") DEPLOY,
    @JsonProperty("overload") OVERLOAD,
    @JsonProperty("realtime_disabled") REALTIME_DISABLED
}
