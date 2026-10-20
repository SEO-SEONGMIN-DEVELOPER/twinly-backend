package com.nidus.twinly.admin.dto.command;

import com.nidus.twinly.report.domain.ReportStatus;

public record AdminUserReportReviewCommand(
        Long reportId,
        ReportStatus status,
        String reviewNote,
        String actionNote
) {
}
