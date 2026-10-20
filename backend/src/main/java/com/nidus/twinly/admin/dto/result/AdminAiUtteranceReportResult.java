package com.nidus.twinly.admin.dto.result;

import com.nidus.twinly.report.domain.ReportStatus;
import com.nidus.twinly.report.domain.ViolationKind;

import java.time.Instant;
import java.time.OffsetDateTime;

public record AdminAiUtteranceReportResult(
        Long id,
        AdminMemberSummaryResult reporter,
        AdminMemberSummaryResult reported,
        Long sceneId,
        String scenePlace,
        OffsetDateTime sceneStartsAt,
        String utteranceText,
        String reason,
        ReportStatus status,
        ViolationKind violationKind,
        String reviewNote,
        String actionNote,
        Instant processedAt,
        Instant createdAt
) {
}
