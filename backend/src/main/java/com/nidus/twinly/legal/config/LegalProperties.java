package com.nidus.twinly.legal.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Map;

@Validated
@ConfigurationProperties(prefix = "legal")
public record LegalProperties(
        @NotBlank @Pattern(regexp = "^https://.+") String baseUrl,
        Map<String, String> minimumVersions
) {

    public LegalProperties {
        minimumVersions = minimumVersions == null ? Map.of() : Map.copyOf(minimumVersions);
    }
}
