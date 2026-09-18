package com.nidus.twinly.auth.config;

import com.nidus.twinly.common.config.RequiredProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "nice")
public record NiceProperties(
        String clientId,
        String clientSecret,
        String returnUrl,
        String closeUrl,
        Duration connectTimeout,
        Duration readTimeout,
        Duration tokenRefreshMargin
) {

    public NiceProperties {
        RequiredProperty.require("nice.client-id", clientId);
        RequiredProperty.require("nice.client-secret", clientSecret);
        RequiredProperty.require("nice.return-url", returnUrl);
        RequiredProperty.require("nice.close-url", closeUrl);
        RequiredProperty.requirePositive("nice.connect-timeout", connectTimeout);
        RequiredProperty.requirePositive("nice.read-timeout", readTimeout);
        RequiredProperty.requirePositive("nice.token-refresh-margin", tokenRefreshMargin);
    }
}
