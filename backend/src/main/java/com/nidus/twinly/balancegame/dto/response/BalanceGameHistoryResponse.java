package com.nidus.twinly.balancegame.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.balancegame.dto.result.BalanceGameHistoryResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record BalanceGameHistoryResponse(
        List<BalanceGameHistoryItemResponse> questions,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @Schema(nullable = true, description = "이번 응답의 마지막 회차 roundId, 더 이전 회차가 없으면 null")
        Long nextCursor
) {

    public static BalanceGameHistoryResponse from(BalanceGameHistoryResult result) {
        return new BalanceGameHistoryResponse(
                result.questions().stream().map(BalanceGameHistoryItemResponse::from).toList(),
                result.nextCursor()
        );
    }
}
