package com.nidus.twinly.me.dto.response;

import com.nidus.twinly.me.dto.result.MeStatusWithdrawalResult;

import java.time.Instant;

public record MeStatusWithdrawalResponse(
        Boolean isDeleted,
        Instant recoverableUntil
) {

    public static MeStatusWithdrawalResponse from(MeStatusWithdrawalResult result) {
        return new MeStatusWithdrawalResponse(
                result.isDeleted(),
                result.recoverableUntil()
        );
    }
}
