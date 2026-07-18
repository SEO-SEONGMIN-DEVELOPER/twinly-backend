package com.nidus.twinly.activity.dto.result;

import java.time.LocalTime;
import java.util.List;

public record ActivityQuestionResult(
        Long id,
        String type,
        LocalTime time,
        String text,
        List<String> options
) {
}
