package com.nidus.twinly.invite.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum InviteRewardMethod {
    @JsonProperty("promotional") PROMOTIONAL,
    @JsonProperty("playDefer") PLAY_DEFER,
    @JsonProperty("appleOffer") APPLE_OFFER
}
