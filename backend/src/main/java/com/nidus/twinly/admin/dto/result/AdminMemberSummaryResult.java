package com.nidus.twinly.admin.dto.result;

import com.nidus.twinly.admin.domain.MemberStatus;
import com.nidus.twinly.common.domain.Gender;

public record AdminMemberSummaryResult(
        Long id,
        String nickname,
        Gender gender,
        String organization,
        MemberStatus status
) {
}
