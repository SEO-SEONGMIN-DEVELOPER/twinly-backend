package com.nidus.twinly.world.dto.command;

import java.io.InputStream;
import java.time.LocalDate;

public record WorldTrackSaveCommand(
        int poolNumber,
        LocalDate date,
        String sectorCode,
        String contentEncoding,
        InputStream body
) {
}
