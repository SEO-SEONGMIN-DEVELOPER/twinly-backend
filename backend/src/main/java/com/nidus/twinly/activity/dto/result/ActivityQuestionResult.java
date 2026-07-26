package com.nidus.twinly.activity.dto.result;

import java.time.OffsetDateTime;
import java.util.List;

public record ActivityQuestionResult(
        Long id,
        String type,
        OffsetDateTime time,
        String text,
        List<String> options
) {
}
