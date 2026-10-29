package com.nidus.twinly.activity.dto.result;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ActivityResult(
        Long userId,
        Long seasonId,
        LocalDate date,
        String version,
        Instant serverNow,
        List<ActivitySceneResult> scenes,
        List<ActivityQuestionResult> questions,
        List<ActivityUserInfoResult> userInfos,
        JsonNode room,
        JsonNode homeObjects
) {
}
