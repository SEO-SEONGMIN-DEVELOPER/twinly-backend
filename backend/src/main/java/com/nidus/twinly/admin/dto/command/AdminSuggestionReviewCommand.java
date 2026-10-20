package com.nidus.twinly.admin.dto.command;

import com.nidus.twinly.common.feedback.FeedbackStatus;

public record AdminSuggestionReviewCommand(
        Long feedbackId,
        FeedbackStatus status,
        String memo
) {
}
