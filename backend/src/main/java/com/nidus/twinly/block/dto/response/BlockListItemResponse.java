package com.nidus.twinly.block.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.block.dto.result.BlockListItemResult;

public record BlockListItemResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long blockedUserId,
        String blockedUserName
) {

    public static BlockListItemResponse from(BlockListItemResult result) {
        return new BlockListItemResponse(result.blockedUserId(), result.blockedUserName());
    }
}
