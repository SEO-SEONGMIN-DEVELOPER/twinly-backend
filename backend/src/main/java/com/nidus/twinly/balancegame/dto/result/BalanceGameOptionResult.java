package com.nidus.twinly.balancegame.dto.result;

import com.nidus.twinly.common.balancegame.BalanceGameOption;

public record BalanceGameOptionResult(
        Long id,
        String label
) {

    public static BalanceGameOptionResult from(BalanceGameOption option) {
        return new BalanceGameOptionResult(option.id(), option.label());
    }
}
