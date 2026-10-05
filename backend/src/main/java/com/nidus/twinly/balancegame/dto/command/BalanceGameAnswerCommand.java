package com.nidus.twinly.balancegame.dto.command;

import com.nidus.twinly.balancegame.dto.request.BalanceGameAnswerRequest;

public record BalanceGameAnswerCommand(
        Long partnerId,
        Long optionId
) {

    public static BalanceGameAnswerCommand from(BalanceGameAnswerRequest request) {
        return new BalanceGameAnswerCommand(request.partnerId(), request.optionId());
    }
}
