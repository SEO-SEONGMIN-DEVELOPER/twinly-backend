package com.nidus.twinly.app.domain;

public record AppVersionPolicy(
        AppVersion minVersion,
        String storeUrl
) {
}
