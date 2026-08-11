package com.nidus.twinly.common.logging;

import org.slf4j.Logger;
import org.slf4j.spi.LoggingEventBuilder;

public final class ErrorLog {

    private static final String ERROR_CODE = "errorCode";
    private static final String ACTOR = "actor";

    private ErrorLog() {
    }

    public static LoggingEventBuilder error(Logger log, String errorCode, String actor, Throwable cause) {
        return build(log.atError(), errorCode, actor, cause);
    }

    public static LoggingEventBuilder warn(Logger log, String errorCode, String actor, Throwable cause) {
        return build(log.atWarn(), errorCode, actor, cause);
    }

    private static LoggingEventBuilder build(LoggingEventBuilder builder, String errorCode, String actor, Throwable cause) {
        if (errorCode != null) {
            builder = builder.addKeyValue(ERROR_CODE, errorCode);
        }
        if (actor != null) {
            builder = builder.addKeyValue(ACTOR, actor);
        }
        if (cause != null) {
            builder = builder.setCause(cause);
        }
        return builder;
    }
}
