package com.nidus.twinly.showcase.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.showcase.dto.result.ShowcaseDialogueSceneResult;

import java.time.OffsetDateTime;
import java.util.List;

public record ShowcaseDialogueSceneResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long sceneId,
        String type,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        String place,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        List<Long> with,
        List<ShowcaseLineResponse> lines
) implements ShowcaseSceneResponse {

    public static ShowcaseDialogueSceneResponse from(ShowcaseDialogueSceneResult result) {
        return new ShowcaseDialogueSceneResponse(
                result.sceneId(),
                result.type(),
                result.startsAt(),
                result.endsAt(),
                result.place(),
                result.with(),
                result.lines().stream().map(ShowcaseLineResponse::from).toList()
        );
    }
}
