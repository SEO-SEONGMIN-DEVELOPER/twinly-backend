package com.nidus.twinly.balancegame.dto.result;

public record BalanceGameHistoryItemResult(
        Long roundId,
        BalanceGameQuestionResult question,
        Long myOptionId,
        Boolean matched
) {
}
