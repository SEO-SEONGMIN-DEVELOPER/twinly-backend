package com.nidus.twinly.season.event;

import java.time.Instant;

public record SeasonParticipationStartedEvent(
        Long userId,
        Instant participatedAt
) {
}
