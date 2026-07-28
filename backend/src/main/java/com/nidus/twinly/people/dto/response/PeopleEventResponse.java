package com.nidus.twinly.people.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.people.dto.result.PeopleEventResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

public record PeopleEventResponse(
        LocalDate date,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long userId,
        @Schema(nullable = true)
        String version,
        List<PeopleEventSceneResponse> scenes,
        List<PeopleEventProfilePhotoResponse> profilePhotos
) {

    public static PeopleEventResponse from(PeopleEventResult result) {
        return new PeopleEventResponse(
                result.date(),
                result.userId(),
                result.version(),
                result.scenes().stream().map(PeopleEventSceneResponse::from).toList(),
                result.profilePhotos().stream().map(PeopleEventProfilePhotoResponse::from).toList()
        );
    }
}
