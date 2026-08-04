package com.nidus.twinly.season.dto.command;

import com.nidus.twinly.season.dto.request.SeasonChangeRequest;

import java.time.Instant;

public record SeasonChangeCommand(
        Instant startedAt,
        Instant endedAt
) {

    public static SeasonChangeCommand from(SeasonChangeRequest request) {
        return new SeasonChangeCommand(request.startedAt(), request.endedAt());
    }
}
