package com.nidus.twinly.block.dto.response;

import com.nidus.twinly.block.dto.result.BlockListItem;
import com.nidus.twinly.block.dto.result.BlockListResult;

import java.util.List;

public record BlockListResponse(
        List<BlockListItem> blocks
) {

    public static BlockListResponse from(BlockListResult result) {
        return new BlockListResponse(result.blocks());
    }
}
