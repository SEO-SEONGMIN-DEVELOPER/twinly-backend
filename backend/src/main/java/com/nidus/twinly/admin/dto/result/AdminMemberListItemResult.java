package com.nidus.twinly.admin.dto.result;

import com.nidus.twinly.admin.domain.MemberStatus;
import com.nidus.twinly.common.domain.Gender;

import java.time.Instant;

public record AdminMemberListItemResult(
        Long id,
        String nickname,
        Gender gender,
        String maskedEmail,
        String organization,
        Instant createdAt,
        MemberStatus status,
        AdminSubscriptionResult subscription
) {
}
