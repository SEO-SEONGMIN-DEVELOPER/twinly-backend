package com.nidus.twinly.app.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Arrays;
import java.util.Optional;

public enum AppPlatform {

    @JsonProperty("ios") IOS("ios"),
    @JsonProperty("android") ANDROID("android");

    private final String headerValue;

    AppPlatform(String headerValue) {
        this.headerValue = headerValue;
    }

    public static Optional<AppPlatform> fromHeader(String headerValue) {
        if (headerValue == null) {
            return Optional.empty();
        }

        String normalized = headerValue.trim();

        return Arrays.stream(values())
                .filter(platform -> platform.headerValue.equalsIgnoreCase(normalized))
                .findFirst();
    }
}
