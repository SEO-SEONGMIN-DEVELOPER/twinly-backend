package com.nidus.twinly.me.dto.result;

import java.util.List;

public record MePersonalityTypeResult(
        String name,
        List<String> keywords,
        MePersonalityTypePartResult adjective,
        MePersonalityTypePartResult noun,
        String imageUrl
) {
}
