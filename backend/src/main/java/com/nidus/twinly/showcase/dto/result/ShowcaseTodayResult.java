package com.nidus.twinly.showcase.dto.result;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ShowcaseTodayResult(
        Long showcaseId,
        Long userRef,
        LocalDate date,
        Instant serverNow,
        List<ShowcaseSceneResult> scenes,
        List<ShowcaseUserInfoResult> userInfos,
        ShowcaseUserCountsResult userCounts
) {
}
