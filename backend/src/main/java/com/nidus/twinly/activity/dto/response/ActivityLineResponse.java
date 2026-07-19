package com.nidus.twinly.activity.dto.response;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.nidus.twinly.activity.dto.result.ActivityBubbleLineResult;
import com.nidus.twinly.activity.dto.result.ActivityLineResult;
import com.nidus.twinly.activity.dto.result.ActivityNarrationLineResult;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "t")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ActivityNarrationLineResponse.class, name = "narr"),
        @JsonSubTypes.Type(value = ActivityBubbleLineResponse.class, name = "bubble")
})
public sealed interface ActivityLineResponse permits ActivityNarrationLineResponse, ActivityBubbleLineResponse {

    static ActivityLineResponse from(ActivityLineResult result) {
        return switch (result) {
            case ActivityNarrationLineResult r -> ActivityNarrationLineResponse.from(r);
            case ActivityBubbleLineResult r -> ActivityBubbleLineResponse.from(r);
        };
    }
}
