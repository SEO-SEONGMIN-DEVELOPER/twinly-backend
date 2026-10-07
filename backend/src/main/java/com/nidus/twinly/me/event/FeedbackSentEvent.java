package com.nidus.twinly.me.event;

import com.nidus.twinly.app.domain.AppPlatform;
import com.nidus.twinly.common.feedback.FeedbackType;

import java.util.List;

public record FeedbackSentEvent(
        Long feedbackId,
        Long userId,
        FeedbackType type,
        List<String> optionLabels,
        String detail,
        AppPlatform appPlatform,
        String appVersion
) {
}
