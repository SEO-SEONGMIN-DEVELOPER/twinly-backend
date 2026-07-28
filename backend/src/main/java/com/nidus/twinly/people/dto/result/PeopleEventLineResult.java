package com.nidus.twinly.people.dto.result;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "t")
@JsonSubTypes({
        @JsonSubTypes.Type(value = PeopleEventNarrationLineResult.class, name = "narr"),
        @JsonSubTypes.Type(value = PeopleEventBubbleLineResult.class, name = "bubble")
})
public sealed interface PeopleEventLineResult permits PeopleEventNarrationLineResult, PeopleEventBubbleLineResult {
}
