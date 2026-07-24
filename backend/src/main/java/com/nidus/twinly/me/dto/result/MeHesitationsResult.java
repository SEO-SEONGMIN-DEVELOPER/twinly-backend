package com.nidus.twinly.me.dto.result;

import java.time.LocalDate;
import java.util.List;

public record MeHesitationsResult(
        LocalDate date,
        List<Long> hesitationIds
) {
}
