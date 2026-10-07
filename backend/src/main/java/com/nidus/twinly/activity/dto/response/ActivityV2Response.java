package com.nidus.twinly.activity.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.activity.dto.result.ActivityResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ActivityV2Response(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long userId,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long seasonId,
        LocalDate date,
        @Schema(nullable = true)
        String version,
        Instant serverNow,
        List<ActivityV2SceneResponse> scenes,
        List<ActivityQuestionResponse> questions,
        List<ActivityUserInfoResponse> userInfos
) {

    public static ActivityV2Response from(ActivityResult result) {
        return new ActivityV2Response(
                result.userId(),
                result.seasonId(),
                result.date(),
                result.version(),
                result.serverNow(),
                result.scenes().stream().map(ActivityV2SceneResponse::from).toList(),
                result.questions().stream().map(ActivityQuestionResponse::from).toList(),
                result.userInfos().stream().map(ActivityUserInfoResponse::from).toList()
        );
    }
}
