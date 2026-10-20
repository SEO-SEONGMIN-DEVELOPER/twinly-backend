package com.nidus.twinly.admin.dto.request;

import com.nidus.twinly.report.domain.ReportStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminUserReportReviewRequest(
        @NotNull ReportStatus status,
        @Size(max = 2000) String reviewNote,
        @Size(max = 2000) String actionNote
) {
}
