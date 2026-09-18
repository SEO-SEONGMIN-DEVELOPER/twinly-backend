package com.nidus.twinly.common.config;

import java.time.Duration;

public final class RequiredProperty {

    private static final String UNRESOLVED_PLACEHOLDER_PREFIX = "${";

    private RequiredProperty() {
    }

    public static void require(String key, String value) {
        if (value == null || value.isBlank() || value.startsWith(UNRESOLVED_PLACEHOLDER_PREFIX)) {
            throw new IllegalStateException(key + " 가 설정되지 않았습니다.");
        }
    }

    public static void requirePositive(String key, Duration value) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException(key + " 는 0보다 커야 합니다.");
        }
    }

    public static void requirePositive(String key, int value) {
        if (value <= 0) {
            throw new IllegalStateException(key + " 는 0보다 커야 합니다.");
        }
    }
}
