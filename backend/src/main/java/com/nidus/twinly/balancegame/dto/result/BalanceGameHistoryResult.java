package com.nidus.twinly.balancegame.dto.result;

import java.util.List;

public record BalanceGameHistoryResult(
        List<BalanceGameHistoryItemResult> questions,
        Long nextCursor
) {
}
