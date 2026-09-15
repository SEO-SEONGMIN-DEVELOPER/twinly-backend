package com.nidus.twinly.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nice")
public record NiceProperties(
        String clientId,
        String clientSecret,
        String returnUrl,
        String closeUrl
) {

    private static final String UNRESOLVED_PLACEHOLDER_PREFIX = "${";

    public NiceProperties {
        requireConfigured(clientId, "nice.client-id");
        requireConfigured(clientSecret, "nice.client-secret");
        requireConfigured(returnUrl, "nice.return-url");
        requireConfigured(closeUrl, "nice.close-url");
    }

    private static void requireConfigured(String value, String key) {
        if (value == null || value.isBlank() || value.startsWith(UNRESOLVED_PLACEHOLDER_PREFIX)) {
            throw new IllegalStateException(key + " 가 설정되지 않았습니다.");
        }
    }
}
