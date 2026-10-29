package com.nidus.twinly.world.controller;

import com.nidus.twinly.world.dto.command.WorldTrackSaveCommand;
import com.nidus.twinly.world.dto.request.WorldTrackFormat;
import com.nidus.twinly.world.service.WorldTrackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.time.LocalDate;

@Tag(name = "세계 트랙")
@RestController
@RequiredArgsConstructor
public class WorldTrackController {

    private final WorldTrackService worldTrackService;

    @Operation(summary = "세계 트랙 저장",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Content-Encoding: gzip 으로 압축한 세계 트랙",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object"))))
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "INVALID_REQUEST"),
            @ApiResponse(responseCode = "413", description = "WORLD_TRACK_TOO_LARGE"),
            @ApiResponse(responseCode = "415", description = "UNSUPPORTED_MEDIA_TYPE (Accept-Encoding: gzip)"),
            @ApiResponse(responseCode = "429", description = "WORLD_TRACK_SAVE_BUSY")
    })
    @PutMapping("/internal/v1/pools/{poolNumber}/tracks/{date}/{sectorCode}")
    public void saveTrack(@PathVariable @Positive int poolNumber,
                          @PathVariable LocalDate date,
                          @PathVariable @Pattern(regexp = WorldTrackFormat.SECTOR_CODE) String sectorCode,
                          @RequestHeader(value = HttpHeaders.CONTENT_ENCODING, required = false) String contentEncoding,
                          InputStream body) {
        worldTrackService.saveTrack(new WorldTrackSaveCommand(poolNumber, date, sectorCode, contentEncoding, body));
    }
}
