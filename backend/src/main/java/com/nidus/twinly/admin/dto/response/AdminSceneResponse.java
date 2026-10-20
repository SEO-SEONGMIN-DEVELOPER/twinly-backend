package com.nidus.twinly.admin.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.admin.dto.result.AdminSceneResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

public record AdminSceneResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long id,
        @Schema(description = "ACTION, DIALOGUE, MOVE")
        String type,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        @Schema(nullable = true)
        String place,
        @Schema(nullable = true)
        String fromPlace,
        @Schema(nullable = true)
        String travelMode,
        @Schema(nullable = true)
        String narration,
        @Schema(nullable = true)
        String mind,
        List<String> partnerNicknames,
        List<Line> lines
) {

    public static AdminSceneResponse from(AdminSceneResult result) {
        return new AdminSceneResponse(
                result.id(),
                result.type(),
                result.startsAt(),
                result.endsAt(),
                result.place(),
                result.fromPlace(),
                result.travelMode(),
                result.narration(),
                result.mind(),
                result.partnerNicknames(),
                result.lines().stream().map(line -> new Line(line.speaker(), line.action(), line.text())).toList()
        );
    }

    public record Line(
            @Schema(nullable = true, description = "비어 있으면 내레이션")
            String speaker,
            @Schema(nullable = true)
            String action,
            @Schema(nullable = true)
            String text
    ) {
    }
}
