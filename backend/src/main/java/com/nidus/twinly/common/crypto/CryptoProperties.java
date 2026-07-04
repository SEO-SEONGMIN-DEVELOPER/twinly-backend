package com.nidus.twinly.common.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConfigurationProperties(prefix = "crypto")
public record CryptoProperties(Map<String, String> aesKeys, String currentVersion) {
}