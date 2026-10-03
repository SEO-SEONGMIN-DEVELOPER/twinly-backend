package com.nidus.twinly.common.balancegame;

import java.util.List;

public record BalanceGameQuestion(
        Long id,
        String text,
        List<BalanceGameOption> options
) {

    public boolean hasOption(Long optionId) {
        return options.stream().anyMatch(option -> option.id().equals(optionId));
    }
}
