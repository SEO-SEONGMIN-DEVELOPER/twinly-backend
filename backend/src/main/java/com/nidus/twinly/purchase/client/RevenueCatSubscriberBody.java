package com.nidus.twinly.purchase.client;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;

public record RevenueCatSubscriberBody(
        Subscriber subscriber
) {

    public record Subscriber(
            Map<String, Entitlement> entitlements
    ) {
    }

    public record Entitlement(
            @JsonProperty("expires_date")
            Instant expiresDate
    ) {
    }
}
