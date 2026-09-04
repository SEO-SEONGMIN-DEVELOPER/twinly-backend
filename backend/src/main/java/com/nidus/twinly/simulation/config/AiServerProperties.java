package com.nidus.twinly.simulation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai-server")
public record AiServerProperties(
        String baseUrl
) {

    private static final String UNRESOLVED_PLACEHOLDER_PREFIX = "${";

    public AiServerProperties {
        if (baseUrl == null || baseUrl.isBlank() || baseUrl.startsWith(UNRESOLVED_PLACEHOLDER_PREFIX)) {
            throw new IllegalStateException("ai-server.base-url 가 설정되지 않았습니다.");
        }
    }
}
