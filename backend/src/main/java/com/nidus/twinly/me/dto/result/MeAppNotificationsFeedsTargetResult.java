package com.nidus.twinly.me.dto.result;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "kind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = MeAppNotificationsFeedsProfileTargetResult.class, name = "profile"),
        @JsonSubTypes.Type(value = MeAppNotificationsFeedsChatTargetResult.class, name = "chat")
})
public sealed interface MeAppNotificationsFeedsTargetResult permits MeAppNotificationsFeedsProfileTargetResult, MeAppNotificationsFeedsChatTargetResult {
}
