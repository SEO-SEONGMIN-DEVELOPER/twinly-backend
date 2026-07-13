package com.nidus.twinly.me.dto.result;

import java.time.Instant;

public record MeWithdrawResult(
        Boolean withdrawalRequested,
        Instant recoverableUntil
) {
}
