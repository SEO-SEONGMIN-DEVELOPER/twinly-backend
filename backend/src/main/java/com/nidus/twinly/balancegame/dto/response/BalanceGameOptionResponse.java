package com.nidus.twinly.balancegame.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.balancegame.dto.result.BalanceGameOptionResult;

public record BalanceGameOptionResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long id,
        String label
) {

    public static BalanceGameOptionResponse from(BalanceGameOptionResult result) {
        return new BalanceGameOptionResponse(result.id(), result.label());
    }
}
