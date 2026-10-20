package com.nidus.twinly.admin.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.admin.dto.result.AdminUserReportResult;
import com.nidus.twinly.report.domain.ReportReason;
import com.nidus.twinly.report.domain.ReportStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record AdminUserReportResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long id,
        AdminMemberSummaryResponse reporter,
        AdminMemberSummaryResponse reported,
        ReportReason reason,
        @Schema(nullable = true)
        String detail,
        ReportStatus status,
        @Schema(nullable = true)
        String reviewNote,
        @Schema(nullable = true)
        String actionNote,
        @Schema(nullable = true)
        Instant processedAt,
        Instant createdAt
) {

    public static AdminUserReportResponse from(AdminUserReportResult result) {
        return new AdminUserReportResponse(
                result.id(),
                AdminMemberSummaryResponse.from(result.reporter()),
                AdminMemberSummaryResponse.from(result.reported()),
                result.reason(),
                result.detail(),
                result.status(),
                result.reviewNote(),
                result.actionNote(),
                result.processedAt(),
                result.createdAt());
    }
}
