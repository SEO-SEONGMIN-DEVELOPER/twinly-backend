package com.nidus.twinly.me.dto.response;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.nidus.twinly.me.dto.result.MeAppNotificationsFeedsBalanceGameTargetResult;
import com.nidus.twinly.me.dto.result.MeAppNotificationsFeedsChatTargetResult;
import com.nidus.twinly.me.dto.result.MeAppNotificationsFeedsPeopleTargetResult;
import com.nidus.twinly.me.dto.result.MeAppNotificationsFeedsProfileTargetResult;
import com.nidus.twinly.me.dto.result.MeAppNotificationsFeedsSeasonRecapTargetResult;
import com.nidus.twinly.me.dto.result.MeAppNotificationsFeedsTargetResult;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "kind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = MeAppNotificationsFeedsProfileTargetResponse.class, name = "profile"),
        @JsonSubTypes.Type(value = MeAppNotificationsFeedsChatTargetResponse.class, name = "chat"),
        @JsonSubTypes.Type(value = MeAppNotificationsFeedsPeopleTargetResponse.class, name = "people"),
        @JsonSubTypes.Type(value = MeAppNotificationsFeedsBalanceGameTargetResponse.class, name = "balanceGame"),
        @JsonSubTypes.Type(value = MeAppNotificationsFeedsSeasonRecapTargetResponse.class, name = "seasonRecap")
})
public sealed interface MeAppNotificationsFeedsTargetResponse permits MeAppNotificationsFeedsProfileTargetResponse, MeAppNotificationsFeedsChatTargetResponse, MeAppNotificationsFeedsPeopleTargetResponse, MeAppNotificationsFeedsBalanceGameTargetResponse, MeAppNotificationsFeedsSeasonRecapTargetResponse {

    static MeAppNotificationsFeedsTargetResponse from(MeAppNotificationsFeedsTargetResult result) {
        return switch (result) {
            case MeAppNotificationsFeedsProfileTargetResult r -> MeAppNotificationsFeedsProfileTargetResponse.from(r);
            case MeAppNotificationsFeedsChatTargetResult r -> MeAppNotificationsFeedsChatTargetResponse.from(r);
            case MeAppNotificationsFeedsPeopleTargetResult r -> MeAppNotificationsFeedsPeopleTargetResponse.from(r);
            case MeAppNotificationsFeedsBalanceGameTargetResult r -> MeAppNotificationsFeedsBalanceGameTargetResponse.from(r);
            case MeAppNotificationsFeedsSeasonRecapTargetResult r -> MeAppNotificationsFeedsSeasonRecapTargetResponse.from(r);
        };
    }
}
