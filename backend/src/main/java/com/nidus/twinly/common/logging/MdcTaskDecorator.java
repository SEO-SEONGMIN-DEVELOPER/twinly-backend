package com.nidus.twinly.common.logging;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> context = MDC.getCopyOfContextMap();

        return () -> {
            if (context != null) {
                MDC.setContextMap(context);
            }

            try {
                runnable.run();
            } finally {
                MDC.clear();
            }
        };
    }
}
