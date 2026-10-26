package com.nidus.twinly.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "admin")
public record AdminProperties(
        String apiToken,
        List<String> hiddenPhoneNumbers
) {
}
