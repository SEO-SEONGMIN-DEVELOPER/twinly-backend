package com.nidus.twinly.connection.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum ConnectionDrainingScope {

    @JsonProperty("all") ALL,
    @JsonProperty("local") LOCAL
}
