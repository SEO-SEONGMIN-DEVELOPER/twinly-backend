package com.nidus.twinly.block.dto.result;

import java.util.List;

public record BlockListResult(
        List<BlockListItem> blocks
) {
}
