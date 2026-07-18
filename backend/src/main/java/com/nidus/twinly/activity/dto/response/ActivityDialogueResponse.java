package com.nidus.twinly.activity.dto.response;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.nidus.twinly.activity.dto.result.ActivityBubbleDialogueResult;
import com.nidus.twinly.activity.dto.result.ActivityDialogueResult;
import com.nidus.twinly.activity.dto.result.ActivityNarrationDialogueResult;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "t")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ActivityNarrationDialogueResponse.class, name = "narr"),
        @JsonSubTypes.Type(value = ActivityBubbleDialogueResponse.class, name = "bubble")
})
public sealed interface ActivityDialogueResponse permits ActivityNarrationDialogueResponse, ActivityBubbleDialogueResponse {

    static ActivityDialogueResponse from(ActivityDialogueResult result) {
        return switch (result) {
            case ActivityNarrationDialogueResult r -> ActivityNarrationDialogueResponse.from(r);
            case ActivityBubbleDialogueResult r -> ActivityBubbleDialogueResponse.from(r);
        };
    }
}
