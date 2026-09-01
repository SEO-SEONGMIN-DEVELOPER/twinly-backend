package com.nidus.twinly.auth.config;

import com.nidus.twinly.auth.client.PortOneChannelType;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;

@ConfigurationProperties(prefix = "portone")
public record PortOneProperties(
        String apiSecret,
        Set<PortOneChannelType> allowedChannelTypes
) {

    private static final String UNRESOLVED_PLACEHOLDER_PREFIX = "${";

    public PortOneProperties {
        if (apiSecret == null || apiSecret.isBlank() || apiSecret.startsWith(UNRESOLVED_PLACEHOLDER_PREFIX)) {
            throw new IllegalStateException("portone.api-secret 가 설정되지 않았습니다.");
        }

        if (allowedChannelTypes == null || allowedChannelTypes.isEmpty()) {
            throw new IllegalStateException("portone.allowed-channel-types 가 설정되지 않았습니다.");
        }
    }

    public boolean allows(PortOneChannelType channelType) {
        return allowedChannelTypes.contains(channelType);
    }
}
