package com.nidus.twinly.common.solapi;

import com.nidus.twinly.common.config.RequiredProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "solapi")
public record SolapiProperties(
        String apiKey,
        String apiSecretKey,
        String fromNumber
) {

    public SolapiProperties {
        RequiredProperty.require("solapi.api-key", apiKey);
        RequiredProperty.require("solapi.api-secret-key", apiSecretKey);
        RequiredProperty.require("solapi.from-number", fromNumber);
    }
}
