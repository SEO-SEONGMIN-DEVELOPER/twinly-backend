package com.nidus.twinly.admin.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.admin.domain.MemberStatus;
import com.nidus.twinly.admin.dto.result.AdminMemberListItemResult;
import com.nidus.twinly.common.domain.Gender;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record AdminMemberListItemResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long id,
        String nickname,
        Gender gender,
        @Schema(nullable = true)
        String maskedEmail,
        @Schema(nullable = true)
        String organization,
        Instant createdAt,
        MemberStatus status,
        @Schema(nullable = true)
        AdminSubscriptionResponse subscription
) {

    public static AdminMemberListItemResponse from(AdminMemberListItemResult result) {
        return new AdminMemberListItemResponse(
                result.id(),
                result.nickname(),
                result.gender(),
                result.maskedEmail(),
                result.organization(),
                result.createdAt(),
                result.status(),
                AdminSubscriptionResponse.from(result.subscription())
        );
    }
}
