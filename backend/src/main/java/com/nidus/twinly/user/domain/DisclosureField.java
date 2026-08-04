package com.nidus.twinly.user.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum DisclosureField {
    @JsonProperty("affiliation")       AFFILIATION,
    @JsonProperty("affiliationNumber") AFFILIATION_NUMBER
}
