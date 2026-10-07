package com.nidus.twinly.people.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.people.dto.result.PeopleEventResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

public record PeopleEventV2Response(
        LocalDate date,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long userId,
        @Schema(nullable = true)
        String version,
        List<PeopleEventV2SceneResponse> scenes,
        List<PeopleEventUserInfoResponse> userInfos
) {

    public static PeopleEventV2Response from(PeopleEventResult result) {
        return new PeopleEventV2Response(
                result.date(),
                result.userId(),
                result.version(),
                result.scenes().stream().map(PeopleEventV2SceneResponse::from).toList(),
                result.userInfos().stream().map(PeopleEventUserInfoResponse::from).toList()
        );
    }
}
