package com.nidus.twinly.people.dto.response;

import com.nidus.twinly.people.dto.result.PeopleEventResult;

import java.time.LocalDate;
import java.util.List;

public record PeopleEventResponse(
        LocalDate date,
        Long userId,
        String version,
        List<PeopleEventSceneResponse> scenes
) {

    public static PeopleEventResponse from(PeopleEventResult result) {
        return new PeopleEventResponse(
                result.date(),
                result.userId(),
                result.version(),
                result.scenes().stream().map(PeopleEventSceneResponse::from).toList()
        );
    }
}
