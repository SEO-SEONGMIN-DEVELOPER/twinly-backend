package com.nidus.twinly.common.jackson;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JacksonTimeConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
            .withUserConfiguration(JacksonTimeConfig.class);

    @Test
    void instant_serializes_as_kst_offset() {
        runner.run(context -> {
            JsonMapper mapper = context.getBean(JsonMapper.class);
            String json = mapper.writeValueAsString(Instant.parse("2026-07-24T06:30:45Z"));
            assertThat(json).isEqualTo("\"2026-07-24T15:30:45+09:00\"");
        });
    }
}
