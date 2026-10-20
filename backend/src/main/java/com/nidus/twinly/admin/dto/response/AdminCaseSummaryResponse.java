package com.nidus.twinly.admin.dto.response;

import com.nidus.twinly.admin.dto.result.AdminCaseSummaryResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record AdminCaseSummaryResponse(
        @Schema(description = "접수·검토 중인 유저 신고 수")
        Long pendingUserReports,
        @Schema(description = "접수·검토 중인 AI 발화 신고 수")
        Long pendingAiUtteranceReports,
        @Schema(description = "미처리 신고의 신고자 성별 (신고 건수 기준)")
        AdminGenderCountResponse pendingReporters,
        @Schema(description = "접수·검토 중인 건의사항 수")
        Long pendingSuggestions
) {

    public static AdminCaseSummaryResponse from(AdminCaseSummaryResult result) {
        return new AdminCaseSummaryResponse(
                result.pendingUserReports(),
                result.pendingAiUtteranceReports(),
                AdminGenderCountResponse.from(result.pendingReporters()),
                result.pendingSuggestions());
    }
}
