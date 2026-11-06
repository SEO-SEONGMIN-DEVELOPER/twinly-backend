package com.nidus.twinly.invite.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum PromotionalSource {
    @JsonProperty("earlySignup") EARLY_SIGNUP,
    @JsonProperty("inviteReward") INVITE_REWARD
}
