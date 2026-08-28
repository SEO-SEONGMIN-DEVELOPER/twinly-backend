package com.nidus.twinly.purchase;

import com.nidus.twinly.purchase.domain.RevenueCatEnvironment;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "revenue-cat")
public record RevenueCatProperties(
        String webhookSecret,
        String secretApiKey,
        RevenueCatEnvironment environment
) {

    private static final String UNRESOLVED_PLACEHOLDER_PREFIX = "${";

    public RevenueCatProperties {
        requireConfigured("revenue-cat.webhook-secret", webhookSecret);
        requireConfigured("revenue-cat.secret-api-key", secretApiKey);

        if (environment == null) {
            throw new IllegalStateException("revenue-cat.environment 가 설정되지 않았습니다.");
        }
    }

    private static void requireConfigured(String key, String value) {
        if (value == null || value.isBlank() || value.startsWith(UNRESOLVED_PLACEHOLDER_PREFIX)) {
            throw new IllegalStateException(key + " 가 설정되지 않았습니다.");
        }
    }
}
