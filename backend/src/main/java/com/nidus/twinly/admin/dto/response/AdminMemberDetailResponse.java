package com.nidus.twinly.admin.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.admin.domain.MemberStatus;
import com.nidus.twinly.admin.dto.result.AdminMemberDetailResult;
import com.nidus.twinly.common.domain.Gender;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record AdminMemberDetailResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long id,
        String nickname,
        Gender gender,
        @Schema(nullable = true)
        String maskedEmail,
        @Schema(nullable = true)
        String organization,
        @Schema(nullable = true)
        String affiliation,
        Instant createdAt,
        MemberStatus status,
        @Schema(nullable = true)
        Instant withdrawalRequestedAt,
        @Schema(nullable = true)
        Instant deletedAt,
        @Schema(nullable = true)
        Instant identityVerifiedAt,
        @Schema(nullable = true)
        Instant emailVerifiedAt,
        @Schema(nullable = true)
        AdminSubscriptionResponse subscription,
        @Schema(description = "AI 활동 기록이 있는 날짜. 최근 60일치")
        List<LocalDate> sceneDates,
        List<RelatedCase> relatedCases
) {

    public static AdminMemberDetailResponse from(AdminMemberDetailResult result) {
        return new AdminMemberDetailResponse(
                result.id(),
                result.nickname(),
                result.gender(),
                result.maskedEmail(),
                result.organization(),
                result.affiliation(),
                result.createdAt(),
                result.status(),
                result.withdrawalRequestedAt(),
                result.deletedAt(),
                result.identityVerifiedAt(),
                result.emailVerifiedAt(),
                AdminSubscriptionResponse.from(result.subscription()),
                result.sceneDates(),
                result.relatedCases().stream().map(RelatedCase::from).toList()
        );
    }

    public record RelatedCase(
            @Schema(description = "USER_REPORT, AI_UTTERANCE_REPORT, SUGGESTION")
            String category,
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            Long id,
            @Schema(description = "REPORTER, REPORTED, AUTHOR")
            String role,
            @Schema(nullable = true)
            String summary,
            String status,
            Instant createdAt
    ) {

        static RelatedCase from(AdminMemberDetailResult.RelatedCase result) {
            return new RelatedCase(result.category(), result.id(), result.role(), result.summary(), result.status(), result.createdAt());
        }
    }
}
