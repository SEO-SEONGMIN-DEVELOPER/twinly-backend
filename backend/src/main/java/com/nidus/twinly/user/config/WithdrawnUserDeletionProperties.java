package com.nidus.twinly.user.config;

import com.nidus.twinly.common.config.RequiredProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "withdrawn-user-deletion")
public record WithdrawnUserDeletionProperties(
        int chunkSize,
        int maxChunks
) {

    public WithdrawnUserDeletionProperties {
        RequiredProperty.requirePositive("withdrawn-user-deletion.chunk-size", chunkSize);
        RequiredProperty.requirePositive("withdrawn-user-deletion.max-chunks", maxChunks);
    }
}
