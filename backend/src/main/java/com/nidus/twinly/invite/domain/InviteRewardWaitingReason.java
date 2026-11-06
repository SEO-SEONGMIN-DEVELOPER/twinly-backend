package com.nidus.twinly.invite.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum InviteRewardWaitingReason {
    @JsonProperty("storeTrial") STORE_TRIAL,
    @JsonProperty("billingIssue") BILLING_ISSUE
}
