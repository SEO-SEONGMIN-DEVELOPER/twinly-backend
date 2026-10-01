package com.nidus.twinly.me.dto.result;

import java.util.List;

public record MeFeedbackOptionsResult(
        List<MeFeedbackOptionsItemResult> options
) {
}
