package com.nidus.twinly.people.dto.response;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.nidus.twinly.people.dto.result.PeopleEventBubbleLineResult;
import com.nidus.twinly.people.dto.result.PeopleEventLineResult;
import com.nidus.twinly.people.dto.result.PeopleEventNarrationLineResult;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "t")
@JsonSubTypes({
        @JsonSubTypes.Type(value = PeopleEventNarrationLineResponse.class, name = "narr"),
        @JsonSubTypes.Type(value = PeopleEventBubbleLineResponse.class, name = "bubble")
})
public sealed interface PeopleEventLineResponse permits PeopleEventNarrationLineResponse, PeopleEventBubbleLineResponse {

    static PeopleEventLineResponse from(PeopleEventLineResult result) {
        return switch (result) {
            case PeopleEventNarrationLineResult r -> PeopleEventNarrationLineResponse.from(r);
            case PeopleEventBubbleLineResult r -> PeopleEventBubbleLineResponse.from(r);
        };
    }
}
