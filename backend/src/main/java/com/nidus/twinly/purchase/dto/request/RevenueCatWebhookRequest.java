package com.nidus.twinly.purchase.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record RevenueCatWebhookRequest(
        Event event
) {

    public record Event(
            String id,
            String type,
            @JsonProperty("app_user_id")
            String appUserId,
            @JsonProperty("transferred_from")
            List<String> transferredFrom,
            @JsonProperty("transferred_to")
            List<String> transferredTo,
            String environment
    ) {
    }
}
