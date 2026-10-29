package com.nidus.twinly.world.config;

import com.nidus.twinly.common.config.RequiredProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "world.track")
public record WorldTrackProperties(
        DataSize maxCompressedSize,
        DataSize maxDecompressedSize,
        int maxConcurrentSaves
) {

    public WorldTrackProperties {
        RequiredProperty.requirePositive("world.track.max-compressed-size", maxCompressedSize);
        RequiredProperty.requirePositive("world.track.max-decompressed-size", maxDecompressedSize);
        RequiredProperty.requirePositive("world.track.max-concurrent-saves", maxConcurrentSaves);
    }
}
