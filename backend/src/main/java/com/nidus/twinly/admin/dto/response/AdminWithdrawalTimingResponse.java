package com.nidus.twinly.admin.dto.response;

import com.nidus.twinly.admin.domain.WithdrawalStage;
import com.nidus.twinly.admin.dto.result.AdminWithdrawalTimingResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record AdminWithdrawalTimingResponse(
        @Schema(description = "첫 시즌 참가 전에 탈퇴했는지 후에 탈퇴했는지")
        WithdrawalStage stage,
        @Schema(description = "시즌 참가 전이면 가입일, 참가 후면 첫 시즌 참가일부터 탈퇴 사유 접수일까지의 날짜 수(KST 달력 기준, 당일은 0)")
        Long days
) {

    public static AdminWithdrawalTimingResponse from(AdminWithdrawalTimingResult result) {
        return result == null ? null : new AdminWithdrawalTimingResponse(result.stage(), result.days());
    }
}
