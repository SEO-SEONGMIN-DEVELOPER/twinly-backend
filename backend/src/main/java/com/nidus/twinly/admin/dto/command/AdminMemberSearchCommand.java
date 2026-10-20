package com.nidus.twinly.admin.dto.command;

import com.nidus.twinly.admin.domain.MemberSearchKey;
import com.nidus.twinly.admin.domain.MemberStatus;

public record AdminMemberSearchCommand(
        MemberSearchKey key,
        String query,
        MemberStatus status,
        int page,
        int size
) {
}
