package com.nidus.twinly.common.logging;

public record LogField(String key, Object value) {

    public static LogField field(String key, Object value) {
        return new LogField(key, value);
    }
}
