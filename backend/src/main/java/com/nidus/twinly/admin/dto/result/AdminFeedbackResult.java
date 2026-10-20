package com.nidus.twinly.admin.dto.result;

import com.nidus.twinly.common.feedback.FeedbackStatus;

import java.time.Instant;
import java.util.List;

public record AdminFeedbackResult(
        Long id,
        AdminMemberSummaryResult author,
        List<String> optionLabels,
        String detail,
        String appPlatform,
        String appVersion,
        FeedbackStatus status,
        String memo,
        Instant createdAt
) {
}
