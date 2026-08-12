package com.nidus.twinly.user.dto.result;

import java.util.List;

public record UsersResult(
        List<Long> userIds,
        UsersPageResult page
) {
}
