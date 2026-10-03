package com.nidus.twinly.balancegame.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum BalanceGameStatus {
    @JsonProperty("waitingMe")      WAITING_ME,
    @JsonProperty("waitingPartner") WAITING_PARTNER,
    @JsonProperty("matched")        MATCHED,
    @JsonProperty("mismatched")     MISMATCHED
}
