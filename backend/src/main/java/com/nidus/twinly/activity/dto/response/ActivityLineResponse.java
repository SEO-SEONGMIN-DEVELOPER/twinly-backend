package com.nidus.twinly.activity.dto.response;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.nidus.twinly.common.scene.SceneBubbleLine;
import com.nidus.twinly.common.scene.SceneLine;
import com.nidus.twinly.common.scene.SceneNarrationLine;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "t")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ActivityNarrationLineResponse.class, name = "narr"),
        @JsonSubTypes.Type(value = ActivityBubbleLineResponse.class, name = "bubble")
})
public sealed interface ActivityLineResponse permits ActivityNarrationLineResponse, ActivityBubbleLineResponse {

    static ActivityLineResponse from(SceneLine line) {
        return switch (line) {
            case SceneNarrationLine l -> ActivityNarrationLineResponse.from(l);
            case SceneBubbleLine l -> ActivityBubbleLineResponse.from(l);
        };
    }
}
