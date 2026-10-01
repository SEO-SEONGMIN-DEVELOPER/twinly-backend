package com.nidus.twinly.me.dto.result;

import java.util.List;

public record MeTendencyQuestionsItemResult(
        Long id,
        String text,
        List<MeTendencyQuestionsOptionResult> options
) {
}
