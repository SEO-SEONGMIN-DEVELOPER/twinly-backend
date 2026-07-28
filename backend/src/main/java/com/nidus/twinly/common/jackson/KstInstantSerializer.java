package com.nidus.twinly.common.jackson;

import com.nidus.twinly.common.time.KstTimes;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

public class KstInstantSerializer extends ValueSerializer<Instant> {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(KstTimes.ZONE);

    @Override
    public void serialize(Instant value, JsonGenerator gen, SerializationContext ctxt) {
        gen.writeString(FORMATTER.format(value));
    }
}
