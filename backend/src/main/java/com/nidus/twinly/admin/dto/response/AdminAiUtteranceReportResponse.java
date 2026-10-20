package com.nidus.twinly.admin.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.admin.dto.result.AdminAiUtteranceReportResult;
import com.nidus.twinly.report.domain.ReportStatus;
import com.nidus.twinly.report.domain.ViolationKind;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.OffsetDateTime;

public record AdminAiUtteranceReportResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long id,
        AdminMemberSummaryResponse reporter,
        @Schema(description = "신고된 발화를 한 트윈의 회원")
        AdminMemberSummaryResponse reported,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long sceneId,
        @Schema(nullable = true)
        String scenePlace,
        @Schema(nullable = true)
        OffsetDateTime sceneStartsAt,
        String utteranceText,
        String reason,
        ReportStatus status,
        @Schema(nullable = true, description = "AI_UTTERANCE: AI 발화 문제, USER_VIOLATION: 회원 위반, NONE: 문제 없음")
        ViolationKind violationKind,
        @Schema(nullable = true)
        String reviewNote,
        @Schema(nullable = true)
        String actionNote,
        @Schema(nullable = true)
        Instant processedAt,
        Instant createdAt
) {

    public static AdminAiUtteranceReportResponse from(AdminAiUtteranceReportResult result) {
        return new AdminAiUtteranceReportResponse(
                result.id(),
                AdminMemberSummaryResponse.from(result.reporter()),
                AdminMemberSummaryResponse.from(result.reported()),
                result.sceneId(),
                result.scenePlace(),
                result.sceneStartsAt(),
                result.utteranceText(),
                result.reason(),
                result.status(),
                result.violationKind(),
                result.reviewNote(),
                result.actionNote(),
                result.processedAt(),
                result.createdAt());
    }
}
