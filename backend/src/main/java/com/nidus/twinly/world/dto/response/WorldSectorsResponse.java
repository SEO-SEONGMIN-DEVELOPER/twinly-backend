package com.nidus.twinly.world.dto.response;

import com.nidus.twinly.world.dto.result.WorldSectorsResult;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record WorldSectorsResponse(
        String poolNumber,
        LocalDate date,
        String sectorCode,
        String layoutVersion,
        OffsetDateTime serverNow,
        String focusRef,
        @Schema(description = "등장인물. ref·displayName·gender·segments, 나머지 칸은 세계 트랙 그대로")
        List<JsonNode> actors,
        List<JsonNode> talks,
        List<JsonNode> gestures,
        List<JsonNode> objects,
        List<JsonNode> events,
        List<JsonNode> items
) {

    public static WorldSectorsResponse from(WorldSectorsResult result) {
        return new WorldSectorsResponse(
                result.poolNumber(),
                result.date(),
                result.sectorCode(),
                result.layoutVersion(),
                result.serverNow(),
                result.focusRef(),
                List.copyOf(result.actors()),
                List.copyOf(result.talks()),
                List.copyOf(result.gestures()),
                List.copyOf(result.objects()),
                List.copyOf(result.events()),
                List.copyOf(result.items())
        );
    }
}
