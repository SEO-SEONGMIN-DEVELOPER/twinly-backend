package com.nidus.twinly.legal.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum PolicyKind {
    @JsonProperty("onboarding")    ONBOARDING,
    @JsonProperty("parallelEntry") PARALLEL_ENTRY
}
