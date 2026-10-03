package com.nidus.twinly.balancegame.dto.result;

import com.nidus.twinly.balancegame.domain.BalanceGameStatus;

import java.time.Instant;

public record BalanceGameResult(
        Long roundId,
        Long partnerId,
        BalanceGameQuestionResult question,
        Instant endsAt,
        BalanceGameStatus status,
        Long myOptionId,
        Long partnerOptionId,
        Boolean partnerAnswered,
        Integer intimacyBonus
) {
}
