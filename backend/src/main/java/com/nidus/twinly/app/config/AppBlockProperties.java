package com.nidus.twinly.app.config;

import com.nidus.twinly.common.config.RequiredProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app-block")
public record AppBlockProperties(
        Duration cacheTtl
) {

    public AppBlockProperties {
        RequiredProperty.requirePositive("app-block.cache-ttl", cacheTtl);
    }
}
