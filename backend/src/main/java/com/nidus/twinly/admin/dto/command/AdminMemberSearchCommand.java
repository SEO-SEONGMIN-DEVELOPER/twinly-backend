package com.nidus.twinly.admin.dto.command;

import com.nidus.twinly.admin.domain.MemberSearchKey;
import com.nidus.twinly.admin.domain.MemberSort;
import com.nidus.twinly.admin.domain.MemberStatus;

public record AdminMemberSearchCommand(
        MemberSearchKey key,
        String query,
        MemberStatus status,
        MemberSort sort,
        int page,
        int size
) {
}
