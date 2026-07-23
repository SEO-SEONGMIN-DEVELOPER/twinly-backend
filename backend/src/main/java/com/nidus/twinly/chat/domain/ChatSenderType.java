package com.nidus.twinly.chat.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum ChatSenderType {

    @JsonProperty("me") ME,
    @JsonProperty("them") THEM,
    @JsonProperty("system") SYSTEM
}
