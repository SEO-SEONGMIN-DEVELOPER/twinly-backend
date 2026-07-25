package com.nidus.twinly.me.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.me.dto.result.MeHesitationsResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

public record MeHesitationsResponse(
        @Schema(nullable = true)
        LocalDate date,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        List<Long> hesitationIds
) {

    public static MeHesitationsResponse from(MeHesitationsResult result) {
        return new MeHesitationsResponse(result.date(), result.hesitationIds());
    }
}
