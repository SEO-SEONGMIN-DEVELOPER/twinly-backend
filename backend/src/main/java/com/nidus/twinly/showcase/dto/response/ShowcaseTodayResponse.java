package com.nidus.twinly.showcase.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.showcase.dto.result.ShowcaseTodayResult;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ShowcaseTodayResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long showcaseId,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long userRef,
        LocalDate date,
        Instant serverNow,
        List<ShowcaseSceneResponse> scenes,
        List<ShowcaseUserInfoResponse> userInfos,
        ShowcaseUserCountsResponse userCounts
) {

    public static ShowcaseTodayResponse from(ShowcaseTodayResult result) {
        return new ShowcaseTodayResponse(
                result.showcaseId(),
                result.userRef(),
                result.date(),
                result.serverNow(),
                result.scenes().stream().map(ShowcaseSceneResponse::from).toList(),
                result.userInfos().stream().map(ShowcaseUserInfoResponse::from).toList(),
                ShowcaseUserCountsResponse.from(result.userCounts())
        );
    }
}
