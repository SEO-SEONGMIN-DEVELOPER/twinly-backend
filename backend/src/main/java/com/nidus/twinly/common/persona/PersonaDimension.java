package com.nidus.twinly.common.persona;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum PersonaDimension {
    @JsonProperty("openness") OPENNESS,
    @JsonProperty("conscientiousness") CONSCIENTIOUSNESS,
    @JsonProperty("extraversion") EXTRAVERSION,
    @JsonProperty("agreeableness") AGREEABLENESS,
    @JsonProperty("neuroticism") NEUROTICISM,
    @JsonProperty("lifeStyle") LIFE_STYLE,
    @JsonProperty("conflictStyle") CONFLICT_STYLE,
    @JsonProperty("communicationStyle") COMMUNICATION_STYLE,
    @JsonProperty("interest") INTEREST,
    @JsonProperty("detail") DETAIL,
    @JsonProperty("summary") SUMMARY
}
