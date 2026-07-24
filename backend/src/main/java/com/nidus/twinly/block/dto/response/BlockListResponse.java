package com.nidus.twinly.block.dto.response;

import com.nidus.twinly.block.dto.result.BlockListResult;

import java.util.List;

public record BlockListResponse(
        List<BlockListItemResponse> blocks
) {

    public static BlockListResponse from(BlockListResult result) {
        return new BlockListResponse(
                result.blocks().stream().map(BlockListItemResponse::from).toList()
        );
    }
}
