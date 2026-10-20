package com.nidus.twinly.admin.dto.response;

import com.nidus.twinly.admin.dto.result.AdminWithdrawalReasonListResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record AdminWithdrawalReasonListResponse(
        @Schema(description = "탈퇴 사유를 남긴 탈퇴 신청 수")
        Long withdrawalCount,
        @Schema(description = "선택지별 선택 수. 여러 개를 고를 수 있다")
        List<ReasonCount> reasonCounts,
        AdminPageResponse<AdminFeedbackResponse> page
) {

    public static AdminWithdrawalReasonListResponse from(AdminWithdrawalReasonListResult result) {
        return new AdminWithdrawalReasonListResponse(
                result.withdrawalCount(),
                result.reasonCounts().stream().map(count -> new ReasonCount(count.label(), count.count())).toList(),
                AdminPageResponse.from(result.page(), AdminFeedbackResponse::from));
    }

    public record ReasonCount(
            String label,
            Long count
    ) {
    }
}
