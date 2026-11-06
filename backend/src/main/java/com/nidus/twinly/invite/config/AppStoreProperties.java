package com.nidus.twinly.invite.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.stream.Stream;

@ConfigurationProperties(prefix = "invite-reward.app-store")
public record AppStoreProperties(
        String bundleId,
        String issuerId,
        String keyId,
        String privateKey,
        String offerId
) {

    public boolean configured() {
        return Stream.of(bundleId, issuerId, keyId, privateKey, offerId)
                .allMatch(value -> value != null && !value.isBlank());
    }
}
