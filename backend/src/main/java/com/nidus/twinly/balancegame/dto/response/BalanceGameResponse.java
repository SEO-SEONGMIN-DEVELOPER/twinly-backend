package com.nidus.twinly.balancegame.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.balancegame.domain.BalanceGameStatus;
import com.nidus.twinly.balancegame.dto.result.BalanceGameResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record BalanceGameResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long roundId,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long partnerId,
        BalanceGameQuestionResponse question,
        Instant endsAt,
        BalanceGameStatus status,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @Schema(nullable = true)
        Long myOptionId,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @Schema(nullable = true, description = "두 사람이 모두 답한 뒤에만 내려간다")
        Long partnerOptionId,
        Boolean partnerAnswered,
        Integer intimacyBonus
) {

    public static BalanceGameResponse from(BalanceGameResult result) {
        return new BalanceGameResponse(
                result.roundId(),
                result.partnerId(),
                BalanceGameQuestionResponse.from(result.question()),
                result.endsAt(),
                result.status(),
                result.myOptionId(),
                result.partnerOptionId(),
                result.partnerAnswered(),
                result.intimacyBonus()
        );
    }
}
