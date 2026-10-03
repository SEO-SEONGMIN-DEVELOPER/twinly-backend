package com.nidus.twinly.balancegame.dto.result;

import com.nidus.twinly.common.balancegame.BalanceGameQuestion;

import java.util.List;

public record BalanceGameQuestionResult(
        Long id,
        String text,
        List<BalanceGameOptionResult> options
) {

    public static BalanceGameQuestionResult from(BalanceGameQuestion question) {
        return new BalanceGameQuestionResult(
                question.id(),
                question.text(),
                question.options().stream().map(BalanceGameOptionResult::from).toList()
        );
    }
}
