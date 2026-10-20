package com.nidus.twinly.admin.dto.result;

import java.time.OffsetDateTime;
import java.util.List;

public record AdminSceneResult(
        Long id,
        String type,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        String place,
        String fromPlace,
        String travelMode,
        String narration,
        String mind,
        List<String> partnerNicknames,
        List<Line> lines
) {

    public record Line(
            String speaker,
            String action,
            String text
    ) {
    }
}
