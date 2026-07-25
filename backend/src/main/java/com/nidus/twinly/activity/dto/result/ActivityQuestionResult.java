package com.nidus.twinly.activity.dto.result;

import java.time.Instant;
import java.util.List;

public record ActivityQuestionResult(
        Long id,
        String type,
        Instant time,
        String text,
        List<String> options
) {
}
