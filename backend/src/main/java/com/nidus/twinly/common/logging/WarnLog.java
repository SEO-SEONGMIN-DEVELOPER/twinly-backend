package com.nidus.twinly.common.logging;

import org.slf4j.Logger;

public final class WarnLog {

    private WarnLog() {
    }

    public static void log(Logger log, String message, LogField... fields) {
        LogFormat.withFields(log.atWarn(), fields).log(LogFormat.render(message, fields));
    }

    public static void log(Logger log, String message, Throwable cause, LogField... fields) {
        LogFormat.withFields(log.atWarn(), fields).setCause(cause).log(LogFormat.render(message, fields));
    }
}
