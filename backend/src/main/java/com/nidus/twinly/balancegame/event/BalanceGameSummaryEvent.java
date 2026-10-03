package com.nidus.twinly.balancegame.event;

import java.time.Instant;
import java.util.Map;

public record BalanceGameSummaryEvent(
        Long roundId,
        Map<Long, Long> matchedCountByUserId,
        Instant createdAt
) {
}
