package com.nidus.twinly.purchase;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pool")
public record PoolProperties(
        Integer fixedNumber
) {

    public PoolProperties {
        if (fixedNumber != null && fixedNumber <= 0) {
            throw new IllegalStateException("pool.fixed-number 는 0보다 커야 합니다.");
        }
    }
}
