package com.nidus.twinly.common.jackson;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.module.SimpleModule;

import java.time.Instant;

@Configuration
public class JacksonTimeConfig {

    @Bean
    public JacksonModule kstTimeModule() {
        SimpleModule module = new SimpleModule();
        module.addSerializer(Instant.class, new KstInstantSerializer());
        return module;
    }
}
