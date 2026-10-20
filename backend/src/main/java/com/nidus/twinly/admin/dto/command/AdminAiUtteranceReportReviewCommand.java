package com.nidus.twinly.admin.dto.command;

import com.nidus.twinly.report.domain.ReportStatus;
import com.nidus.twinly.report.domain.ViolationKind;

public record AdminAiUtteranceReportReviewCommand(
        Long reportId,
        ReportStatus status,
        ViolationKind violationKind,
        String reviewNote,
        String actionNote
) {
}
