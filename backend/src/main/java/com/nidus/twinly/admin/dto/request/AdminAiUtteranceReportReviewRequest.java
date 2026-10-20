package com.nidus.twinly.admin.dto.request;

import com.nidus.twinly.report.domain.ReportStatus;
import com.nidus.twinly.report.domain.ViolationKind;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminAiUtteranceReportReviewRequest(
        @NotNull ReportStatus status,
        ViolationKind violationKind,
        @Size(max = 2000) String reviewNote,
        @Size(max = 2000) String actionNote
) {
}
