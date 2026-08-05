package com.nidus.twinly.common.fcm;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fcm")
public record FcmProperties(
        String serviceAccountBase64
) {
}
