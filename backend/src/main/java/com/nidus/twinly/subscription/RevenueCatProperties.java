package com.nidus.twinly.subscription;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "revenue-cat")
public record RevenueCatProperties(
        String webhookSecret
) {

    private static final String UNRESOLVED_PLACEHOLDER_PREFIX = "${";

    public RevenueCatProperties {
        if (webhookSecret == null || webhookSecret.isBlank() || webhookSecret.startsWith(UNRESOLVED_PLACEHOLDER_PREFIX)) {
            throw new IllegalStateException("revenue-cat.webhook-secret 이 설정되지 않았습니다.");
        }
    }
}
