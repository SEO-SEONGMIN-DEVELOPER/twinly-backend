package com.nidus.twinly.user.dto.result;

public record UsersPageResult(
        Long nextCursor,
        Boolean hasMore
) {
}
