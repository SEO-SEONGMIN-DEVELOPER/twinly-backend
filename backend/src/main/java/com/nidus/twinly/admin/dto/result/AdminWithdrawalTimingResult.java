package com.nidus.twinly.admin.dto.result;

import com.nidus.twinly.admin.domain.WithdrawalStage;
import com.nidus.twinly.common.time.KstTimes;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public record AdminWithdrawalTimingResult(
        WithdrawalStage stage,
        long days
) {

    public static AdminWithdrawalTimingResult of(Instant joinedAt, Instant subscribedAt, Instant withdrawnAt) {
        boolean subscribed = subscribedAt != null && !subscribedAt.isAfter(withdrawnAt);
        Instant from = subscribed ? subscribedAt : joinedAt;

        return new AdminWithdrawalTimingResult(
                subscribed ? WithdrawalStage.AFTER_SUBSCRIPTION : WithdrawalStage.BEFORE_SUBSCRIPTION,
                Math.max(0, ChronoUnit.DAYS.between(
                        from.atZone(KstTimes.ZONE).toLocalDate(),
                        withdrawnAt.atZone(KstTimes.ZONE).toLocalDate())));
    }
}
