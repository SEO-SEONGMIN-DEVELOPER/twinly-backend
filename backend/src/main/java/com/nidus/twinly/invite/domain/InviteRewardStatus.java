package com.nidus.twinly.invite.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum InviteRewardStatus {
    @JsonProperty("waiting") WAITING,
    @JsonProperty("appleOfferPending") APPLE_OFFER_PENDING,
    @JsonProperty("appleOfferAccepted") APPLE_OFFER_ACCEPTED,
    @JsonProperty("granted") GRANTED
}
