package com.nidus.twinly.common.logging;

import org.slf4j.Logger;

public final class InfoLog {

    private InfoLog() {
    }

    public static void log(Logger log, String message, LogField... fields) {
        LogFormat.withFields(log.atInfo(), fields).log(LogFormat.render(message, fields));
    }
}
