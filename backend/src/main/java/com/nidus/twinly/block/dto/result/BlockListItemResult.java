package com.nidus.twinly.block.dto.result;

public record BlockListItemResult(
        Long blockedUserId,
        String blockedUserName
) {
}
