package com.nidus.twinly.report.event;

import com.nidus.twinly.report.domain.ReportReason;

public record UserReportedEvent(
        Long reportId,
        Long reporterUserId,
        Long reportedUserId,
        ReportReason reason,
        String detail
) {
}
