package com.nidus.twinly.me.dto.result;

import java.time.Instant;

public record MeStatusWithdrawalResult(
        Boolean isDeleted,
        Instant recoverableUntil
) {
}
