package com.nidus.twinly.common.crypto;

import com.nidus.twinly.common.config.RequiredProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "crypto")
public record CryptoProperties(
        Map<String, String> aesKeys,
        String currentVersion,
        String hmacKey
) {

    public CryptoProperties {
        if (aesKeys == null || aesKeys.isEmpty()) {
            throw new IllegalStateException("crypto.aes-keys 가 설정되지 않았습니다.");
        }

        aesKeys.forEach((version, key) -> RequiredProperty.require("crypto.aes-keys." + version, key));
        RequiredProperty.require("crypto.current-version", currentVersion);
        RequiredProperty.require("crypto.hmac-key", hmacKey);
    }
}
