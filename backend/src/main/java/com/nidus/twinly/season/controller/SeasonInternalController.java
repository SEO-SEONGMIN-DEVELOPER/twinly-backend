package com.nidus.twinly.season.controller;

import com.nidus.twinly.common.web.RequestId;
import com.nidus.twinly.season.dto.response.SeasonPoolReassignResponse;
import com.nidus.twinly.season.service.SeasonService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "시즌 (내부)")
@RestController
@RequiredArgsConstructor
public class SeasonInternalController {

    private final SeasonService seasonService;

    @Operation(summary = "시즌 풀 재배정 (시즌당 한 번)")
    @ApiResponse(responseCode = "404", description = "SEASON_NOT_FOUND")
    @ApiResponse(responseCode = "409", description = "SEASON_POOL_REASSIGN_NOT_ALLOWED")
    @PostMapping("/internal/v1/seasons/{seasonId}/pools/reassign")
    public SeasonPoolReassignResponse reassignPools(@PathVariable("seasonId") String seasonId) {
        return SeasonPoolReassignResponse.from(seasonService.reassignPools(RequestId.toLong(seasonId, "seasonId")));
    }
}
