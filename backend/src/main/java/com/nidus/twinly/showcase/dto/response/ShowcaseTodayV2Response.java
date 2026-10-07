package com.nidus.twinly.showcase.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.showcase.dto.result.ShowcaseTodayResult;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ShowcaseTodayV2Response(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long showcaseId,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long userRef,
        LocalDate date,
        Instant serverNow,
        List<ShowcaseV2SceneResponse> scenes,
        List<ShowcaseUserInfoResponse> userInfos,
        ShowcaseUserCountsResponse userCounts
) {

    public static ShowcaseTodayV2Response from(ShowcaseTodayResult result) {
        return new ShowcaseTodayV2Response(
                result.showcaseId(),
                result.userRef(),
                result.date(),
                result.serverNow(),
                result.scenes().stream().map(ShowcaseV2SceneResponse::from).toList(),
                result.userInfos().stream().map(ShowcaseUserInfoResponse::from).toList(),
                ShowcaseUserCountsResponse.from(result.userCounts())
        );
    }
}
