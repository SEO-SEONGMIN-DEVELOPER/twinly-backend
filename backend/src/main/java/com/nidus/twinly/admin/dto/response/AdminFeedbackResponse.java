package com.nidus.twinly.admin.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.admin.dto.result.AdminFeedbackResult;
import com.nidus.twinly.common.feedback.FeedbackStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

public record AdminFeedbackResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long id,
        AdminMemberSummaryResponse author,
        List<String> optionLabels,
        @Schema(nullable = true)
        String detail,
        @Schema(nullable = true)
        String appPlatform,
        @Schema(nullable = true)
        String appVersion,
        FeedbackStatus status,
        @Schema(nullable = true)
        String memo,
        Instant createdAt
) {

    public static AdminFeedbackResponse from(AdminFeedbackResult result) {
        return new AdminFeedbackResponse(
                result.id(),
                AdminMemberSummaryResponse.from(result.author()),
                result.optionLabels(),
                result.detail(),
                result.appPlatform(),
                result.appVersion(),
                result.status(),
                result.memo(),
                result.createdAt());
    }
}
