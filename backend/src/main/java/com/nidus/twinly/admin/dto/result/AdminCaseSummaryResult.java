package com.nidus.twinly.admin.dto.result;

public record AdminCaseSummaryResult(
        long pendingUserReports,
        long pendingAiUtteranceReports,
        AdminGenderCountResult pendingReporters,
        long pendingSuggestions
) {
}
