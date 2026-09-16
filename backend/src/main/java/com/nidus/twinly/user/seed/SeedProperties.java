package com.nidus.twinly.user.seed;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "seed")
public record SeedProperties(
        @DefaultValue("false") boolean aiTestUsers
) {
}
