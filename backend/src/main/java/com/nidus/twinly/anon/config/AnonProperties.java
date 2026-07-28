package com.nidus.twinly.anon.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "anon")
public record AnonProperties(
        @NotNull Duration sessionTtl
) {
}
