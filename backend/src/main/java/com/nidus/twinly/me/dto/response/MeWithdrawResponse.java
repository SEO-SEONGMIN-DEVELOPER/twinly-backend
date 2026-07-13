package com.nidus.twinly.me.dto.response;

import com.nidus.twinly.me.dto.result.MeWithdrawResult;

import java.time.Instant;

public record MeWithdrawResponse(
        Boolean withdrawalRequested,
        Instant recoverableUntil
) {

    public static MeWithdrawResponse from(MeWithdrawResult result) {
        return new MeWithdrawResponse(result.withdrawalRequested(), result.recoverableUntil());
    }
}
