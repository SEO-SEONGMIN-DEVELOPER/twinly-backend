package com.nidus.twinly.simulation.config;

import com.nidus.twinly.common.config.RequiredProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "ai-server")
public record AiServerProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout
) {

    public AiServerProperties {
        RequiredProperty.require("ai-server.base-url", baseUrl);
        RequiredProperty.requirePositive("ai-server.connect-timeout", connectTimeout);
        RequiredProperty.requirePositive("ai-server.read-timeout", readTimeout);
    }
}
