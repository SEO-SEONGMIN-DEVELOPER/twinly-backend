package com.nidus.twinly.device.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum DevicePlatform {
    @JsonProperty("ios")     IOS,
    @JsonProperty("android") ANDROID
}
