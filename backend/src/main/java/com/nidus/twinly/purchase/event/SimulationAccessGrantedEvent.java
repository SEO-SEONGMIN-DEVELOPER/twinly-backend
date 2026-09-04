package com.nidus.twinly.purchase.event;

import java.time.Instant;

public record SimulationAccessGrantedEvent(
        Long userId,
        Instant grantedAt
) {
}
