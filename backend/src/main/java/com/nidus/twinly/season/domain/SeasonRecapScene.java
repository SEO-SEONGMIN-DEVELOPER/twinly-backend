package com.nidus.twinly.season.domain;

import com.nidus.twinly.activity.domain.SceneType;

import java.time.LocalDateTime;
import java.util.Set;

public record SeasonRecapScene(
        Long id,
        SceneType type,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        String place,
        String placeCode,
        Set<Long> partnerUserIds,
        int myBubbleCount
) {
}
