package com.nidus.twinly.common.jackson;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.DefaultContentTypeResolver;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.util.MimeTypeUtils;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class StompJacksonConverterTest {

    record Sample(Instant occurredAt) {
    }

    @Test
    void stomp_converter_serializes_instant_as_kst_offset() {
        JsonMapper mapper = JsonMapper.builder()
                .addModule(new JacksonTimeConfig().kstTimeModule())
                .build();

        DefaultContentTypeResolver resolver = new DefaultContentTypeResolver();
        resolver.setDefaultMimeType(MimeTypeUtils.APPLICATION_JSON);

        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter(mapper);
        converter.setContentTypeResolver(resolver);

        Message<?> message = converter.toMessage(new Sample(Instant.parse("2026-07-24T06:30:45Z")), null);
        String json = new String((byte[]) message.getPayload(), StandardCharsets.UTF_8);

        assertThat(json).contains("\"2026-07-24T15:30:45+09:00\"");
    }
}
