package com.nidus.twinly.admin.dto.result;

import com.nidus.twinly.report.domain.ReportReason;
import com.nidus.twinly.report.domain.ReportStatus;

import java.time.Instant;

public record AdminUserReportResult(
        Long id,
        AdminMemberSummaryResult reporter,
        AdminMemberSummaryResult reported,
        ReportReason reason,
        String detail,
        ReportStatus status,
        String reviewNote,
        String actionNote,
        Instant processedAt,
        Instant createdAt
) {
}
