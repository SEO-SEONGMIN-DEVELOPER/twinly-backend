package com.nidus.twinly.common.persona;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.EnumSet;
import java.util.Set;

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
    @JsonProperty("summary") SUMMARY;

    private static final Set<PersonaDimension> BIG_FIVE =
            EnumSet.of(OPENNESS, CONSCIENTIOUSNESS, EXTRAVERSION, AGREEABLENESS, NEUROTICISM);

    public boolean isBigFive() {
        return BIG_FIVE.contains(this);
    }
}
