package com.nidus.twinly.admin.dto.result;

import com.nidus.twinly.admin.domain.MemberStatus;
import com.nidus.twinly.common.domain.Gender;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record AdminMemberDetailResult(
        Long id,
        String nickname,
        Gender gender,
        String maskedEmail,
        String organization,
        String affiliation,
        Instant createdAt,
        MemberStatus status,
        Instant withdrawalRequestedAt,
        Instant deletedAt,
        Instant identityVerifiedAt,
        Instant emailVerifiedAt,
        AdminSubscriptionResult subscription,
        List<LocalDate> sceneDates,
        List<RelatedCase> relatedCases
) {

    public record RelatedCase(
            String category,
            Long id,
            String role,
            String summary,
            String status,
            Instant createdAt
    ) {
    }
}
