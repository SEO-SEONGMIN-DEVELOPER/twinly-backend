package com.nidus.twinly.showcase.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.showcase.dto.result.ShowcaseTodaySectorsResult;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record ShowcaseTodaySectorsResponse(
        String poolNumber,
        LocalDate date,
        String sectorCode,
        String layoutVersion,
        OffsetDateTime serverNow,
        String focusRef,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long showcaseId,
        @Schema(description = "바뀌면 받아 둔 창과 둘러보기를 버리고 다시 받는다")
        Integer refVersion,
        @Schema(description = "등장인물. ref 는 둘러보기 번호(user:1 이 대상), 나머지 칸은 세계 트랙 그대로")
        List<JsonNode> actors,
        List<JsonNode> talks,
        List<JsonNode> gestures,
        List<JsonNode> objects,
        List<JsonNode> events,
        List<JsonNode> items
) {

    public static ShowcaseTodaySectorsResponse from(ShowcaseTodaySectorsResult result) {
        return new ShowcaseTodaySectorsResponse(
                result.poolNumber(),
                result.date(),
                result.sectorCode(),
                result.layoutVersion(),
                result.serverNow(),
                result.focusRef(),
                result.showcaseId(),
                result.refVersion(),
                List.copyOf(result.actors()),
                List.copyOf(result.talks()),
                List.copyOf(result.gestures()),
                List.copyOf(result.objects()),
                List.copyOf(result.events()),
                List.copyOf(result.items())
        );
    }
}
