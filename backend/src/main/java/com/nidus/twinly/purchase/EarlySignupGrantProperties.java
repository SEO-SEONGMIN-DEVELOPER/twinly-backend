package com.nidus.twinly.purchase;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Instant;

@ConfigurationProperties(prefix = "early-signup-grant")
public record EarlySignupGrantProperties(
        Instant endsAt
) {

    public EarlySignupGrantProperties {
        if (endsAt == null) {
            throw new IllegalStateException("early-signup-grant.ends-at 가 설정되지 않았습니다.");
        }
    }
}
