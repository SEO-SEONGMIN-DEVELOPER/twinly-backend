package com.nidus.twinly.people.dto.result;

import java.time.LocalDate;
import java.util.List;

public record PeopleEventResult(
        LocalDate date,
        Long userId,
        String version,
        List<PeopleEventSceneResult> scenes,
        List<PeopleEventProfilePhotoResult> profilePhotos
) {
}
