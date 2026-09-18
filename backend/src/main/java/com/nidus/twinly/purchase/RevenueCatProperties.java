package com.nidus.twinly.purchase;

import com.nidus.twinly.common.config.RequiredProperty;
import com.nidus.twinly.purchase.domain.RevenueCatEnvironment;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "revenue-cat")
public record RevenueCatProperties(
        String webhookSecret,
        String secretApiKey,
        RevenueCatEnvironment environment,
        Duration connectTimeout,
        Duration readTimeout,
        Duration syncInterval
) {

    public RevenueCatProperties {
        RequiredProperty.require("revenue-cat.webhook-secret", webhookSecret);
        RequiredProperty.require("revenue-cat.secret-api-key", secretApiKey);
        RequiredProperty.requirePositive("revenue-cat.connect-timeout", connectTimeout);
        RequiredProperty.requirePositive("revenue-cat.read-timeout", readTimeout);
        RequiredProperty.requirePositive("revenue-cat.sync-interval", syncInterval);

        if (environment == null) {
            throw new IllegalStateException("revenue-cat.environment 가 설정되지 않았습니다.");
        }
    }
}
