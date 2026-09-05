package com.nidus.twinly.common.logging;

import org.springframework.core.task.TaskDecorator;

public class NewTraceIdTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        return () -> TraceContext.run(TraceContext.newTraceId(), runnable);
    }
}
