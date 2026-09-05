package com.nidus.twinly.common.logging;

import org.slf4j.MDC;

import java.util.UUID;

public final class TraceContext {

    public static final String TRACE_ID = "traceId";

    private static final int TRACE_ID_LENGTH = 8;

    private TraceContext() {
    }

    public static String newTraceId() {
        return UUID.randomUUID().toString().substring(0, TRACE_ID_LENGTH);
    }

    public static String currentTraceId() {
        return MDC.get(TRACE_ID);
    }

    public static void run(String traceId, Runnable action) {
        MDC.put(TRACE_ID, traceId != null ? traceId : newTraceId());

        try {
            action.run();
        } finally {
            MDC.clear();
        }
    }
}
