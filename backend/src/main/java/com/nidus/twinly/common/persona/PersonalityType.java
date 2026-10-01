package com.nidus.twinly.common.persona;

import java.util.List;

public record PersonalityType(
        String code,
        String name,
        List<String> keywords,
        PersonalityTypePart adjective,
        PersonalityTypePart noun,
        String imageKey
) {
}
