package com.nidus.twinly.common.scene;

import com.nidus.twinly.common.time.KstTimes;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record SceneBubbleLine(
        String t,
        Long userId,
        String action,
        String text,
        OffsetDateTime occursAt
) implements SceneLine {

    public static SceneBubbleLine from(StoredSceneBubbleLine line, LocalDate date) {
        return new SceneBubbleLine(
                line.t(),
                line.userId(),
                line.action(),
                line.text(),
                KstTimes.toKstOffsetDateTime(date.atTime(line.occursAt()))
        );
    }
}
