package com.nidus.twinly.people.dto.response;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.nidus.twinly.common.scene.SceneBubbleLine;
import com.nidus.twinly.common.scene.SceneLine;
import com.nidus.twinly.common.scene.SceneNarrationLine;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "t")
@JsonSubTypes({
        @JsonSubTypes.Type(value = PeopleEventV2NarrationLineResponse.class, name = "narr"),
        @JsonSubTypes.Type(value = PeopleEventBubbleLineResponse.class, name = "bubble")
})
public sealed interface PeopleEventV2LineResponse permits PeopleEventV2NarrationLineResponse, PeopleEventBubbleLineResponse {

    static PeopleEventV2LineResponse from(SceneLine line) {
        return switch (line) {
            case SceneNarrationLine l -> PeopleEventV2NarrationLineResponse.from(l);
            case SceneBubbleLine l -> PeopleEventBubbleLineResponse.from(l);
        };
    }
}
