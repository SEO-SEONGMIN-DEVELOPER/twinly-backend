package com.nidus.twinly.admin.dto.request;

import com.nidus.twinly.common.feedback.FeedbackStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminSuggestionReviewRequest(
        @NotNull FeedbackStatus status,
        @Size(max = 2000) String memo
) {
}
