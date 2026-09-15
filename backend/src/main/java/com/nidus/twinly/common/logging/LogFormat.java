package com.nidus.twinly.common.logging;

import org.slf4j.spi.LoggingEventBuilder;

import java.util.Arrays;
import java.util.stream.Collectors;

final class LogFormat {

    private static final String FIELD_SEPARATOR = ", ";
    private static final String MESSAGE_FIELD_SEPARATOR = " ";

    private LogFormat() {
    }

    static LoggingEventBuilder withFields(LoggingEventBuilder builder, LogField[] fields) {
        for (LogField field : fields) {
            builder = builder.addKeyValue(field.key(), field.value());
        }

        return builder;
    }

    static String render(String message, LogField[] fields) {
        if (fields.length == 0) {
            return message;
        }

        String rendered = Arrays.stream(fields)
                .map(field -> field.key() + "=" + field.value())
                .collect(Collectors.joining(FIELD_SEPARATOR));

        return message + MESSAGE_FIELD_SEPARATOR + rendered;
    }
}
