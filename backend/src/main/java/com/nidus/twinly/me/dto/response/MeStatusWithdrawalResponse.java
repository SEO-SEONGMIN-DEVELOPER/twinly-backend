package com.nidus.twinly.me.dto.response;

import com.nidus.twinly.me.dto.result.MeStatusWithdrawalResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record MeStatusWithdrawalResponse(
        Boolean isDeleted,
        @Schema(nullable = true)
        Instant recoverableUntil
) {

    public static MeStatusWithdrawalResponse from(MeStatusWithdrawalResult result) {
        return new MeStatusWithdrawalResponse(
                result.isDeleted(),
                result.recoverableUntil()
        );
    }
}
