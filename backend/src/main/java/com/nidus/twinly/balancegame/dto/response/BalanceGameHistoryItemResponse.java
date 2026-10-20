package com.nidus.twinly.balancegame.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.balancegame.dto.result.BalanceGameHistoryItemResult;

public record BalanceGameHistoryItemResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long roundId,
        BalanceGameQuestionResponse question,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long myOptionId,
        Boolean matched
) {

    public static BalanceGameHistoryItemResponse from(BalanceGameHistoryItemResult result) {
        return new BalanceGameHistoryItemResponse(
                result.roundId(),
                BalanceGameQuestionResponse.from(result.question()),
                result.myOptionId(),
                result.matched()
        );
    }
}
