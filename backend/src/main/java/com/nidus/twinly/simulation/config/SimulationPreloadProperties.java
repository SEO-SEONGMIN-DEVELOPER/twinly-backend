package com.nidus.twinly.simulation.config;

import com.nidus.twinly.common.config.RequiredProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "simulation.preload")
public record SimulationPreloadProperties(
        int days,
        int maxAttempts,
        Duration retryDelay
) {

    public SimulationPreloadProperties {
        RequiredProperty.requirePositive("simulation.preload.days", days);
        RequiredProperty.requirePositive("simulation.preload.max-attempts", maxAttempts);
        RequiredProperty.requirePositive("simulation.preload.retry-delay", retryDelay);
    }
}
