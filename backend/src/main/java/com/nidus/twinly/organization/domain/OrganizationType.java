package com.nidus.twinly.organization.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum OrganizationType {
    @JsonProperty("school")  SCHOOL,
    @JsonProperty("company") COMPANY
}
