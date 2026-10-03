package com.nidus.twinly.balancegame.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.balancegame.dto.result.BalanceGameQuestionResult;

import java.util.List;

public record BalanceGameQuestionResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long id,
        String text,
        List<BalanceGameOptionResponse> options
) {

    public static BalanceGameQuestionResponse from(BalanceGameQuestionResult result) {
        return new BalanceGameQuestionResponse(
                result.id(),
                result.text(),
                result.options().stream().map(BalanceGameOptionResponse::from).toList()
        );
    }
}
