package com.nidus.twinly.activity.dto.result;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "t")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ActivityNarrationLineResult.class, name = "narr"),
        @JsonSubTypes.Type(value = ActivityBubbleLineResult.class, name = "bubble")
})
public sealed interface ActivityLineResult permits ActivityNarrationLineResult, ActivityBubbleLineResult {
}
