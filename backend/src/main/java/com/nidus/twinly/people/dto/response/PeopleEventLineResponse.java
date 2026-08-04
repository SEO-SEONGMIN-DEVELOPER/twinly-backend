package com.nidus.twinly.people.dto.response;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.nidus.twinly.common.scene.SceneBubbleLine;
import com.nidus.twinly.common.scene.SceneLine;
import com.nidus.twinly.common.scene.SceneNarrationLine;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "t")
@JsonSubTypes({
        @JsonSubTypes.Type(value = PeopleEventNarrationLineResponse.class, name = "narr"),
        @JsonSubTypes.Type(value = PeopleEventBubbleLineResponse.class, name = "bubble")
})
public sealed interface PeopleEventLineResponse permits PeopleEventNarrationLineResponse, PeopleEventBubbleLineResponse {

    static PeopleEventLineResponse from(SceneLine line) {
        return switch (line) {
            case SceneNarrationLine l -> PeopleEventNarrationLineResponse.from(l);
            case SceneBubbleLine l -> PeopleEventBubbleLineResponse.from(l);
        };
    }
}
