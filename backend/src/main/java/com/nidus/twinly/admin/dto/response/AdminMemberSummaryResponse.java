package com.nidus.twinly.admin.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.admin.domain.MemberStatus;
import com.nidus.twinly.admin.dto.result.AdminMemberSummaryResult;
import com.nidus.twinly.common.domain.Gender;
import io.swagger.v3.oas.annotations.media.Schema;

public record AdminMemberSummaryResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long id,
        @Schema(nullable = true)
        String nickname,
        @Schema(nullable = true)
        Gender gender,
        @Schema(nullable = true)
        String organization,
        MemberStatus status
) {

    public static AdminMemberSummaryResponse from(AdminMemberSummaryResult result) {
        if (result == null) {
            return null;
        }

        return new AdminMemberSummaryResponse(result.id(), result.nickname(), result.gender(), result.organization(), result.status());
    }
}
