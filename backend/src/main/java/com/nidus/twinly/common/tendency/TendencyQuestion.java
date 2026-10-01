package com.nidus.twinly.common.tendency;

import java.util.List;

public record TendencyQuestion(
        Long id,
        String text,
        List<TendencyOption> options
) {

    public boolean hasOption(Long optionId) {
        return options.stream().anyMatch(option -> option.id().equals(optionId));
    }
}
