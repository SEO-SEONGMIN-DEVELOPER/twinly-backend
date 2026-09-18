package com.nidus.twinly.common.jwt;

import com.nidus.twinly.common.config.RequiredProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String secretKey
) {

    public JwtProperties {
        RequiredProperty.require("jwt.secret-key", secretKey);
    }
}
