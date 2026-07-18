package com.nidus.twinly.activity.dto.result;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "t")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ActivityNarrationDialogueResult.class, name = "narr"),
        @JsonSubTypes.Type(value = ActivityBubbleDialogueResult.class, name = "bubble")
})
public sealed interface ActivityDialogueResult permits ActivityNarrationDialogueResult, ActivityBubbleDialogueResult {
}
