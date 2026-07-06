package com.nidus.twinly.common.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "crypto")
public record CryptoProperties(Map<String, String> aesKeys, String currentVersion, String hmacKey) {
}