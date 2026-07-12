package com.nidus.twinly.common.solapi;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "solapi")
public record SolapiProperties(
        String apiKey,
        String apiSecretKey,
        String fromNumber
) {
}