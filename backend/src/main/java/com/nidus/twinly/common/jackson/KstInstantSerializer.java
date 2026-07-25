package com.nidus.twinly.common.jackson;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class KstInstantSerializer extends ValueSerializer<Instant> {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.of("Asia/Seoul"));

    @Override
    public void serialize(Instant value, JsonGenerator gen, SerializationContext ctxt) {
        gen.writeString(FORMATTER.format(value));
    }
}
