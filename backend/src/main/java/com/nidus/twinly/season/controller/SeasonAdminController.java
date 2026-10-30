package com.nidus.twinly.season.controller;

import com.nidus.twinly.season.dto.command.SeasonChangeCommand;
import com.nidus.twinly.season.dto.request.SeasonChangeRequest;
import com.nidus.twinly.season.dto.response.SeasonChangeResponse;
import com.nidus.twinly.season.dto.response.SeasonPoolReassignResponse;
import com.nidus.twinly.season.dto.response.SeasonReservationResponse;
import com.nidus.twinly.season.service.SeasonReservationService;
import com.nidus.twinly.season.service.SeasonService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@Tag(name = "시즌 (관리자)")
@RestController
@RequiredArgsConstructor
public class SeasonAdminController {

    private final SeasonService seasonService;
    private final SeasonReservationService seasonReservationService;

    @Operation(summary = "시즌 전환")
    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED")
    @ApiResponse(responseCode = "403", description = "FORBIDDEN")
    @ApiResponse(responseCode = "422", description = "INVALID_SEASON_PERIOD")
    @PostMapping("/admin/season")
    public SeasonChangeResponse changeSeason(@Valid @RequestBody SeasonChangeRequest request) {
        return SeasonChangeResponse.from(seasonService.changeSeason(SeasonChangeCommand.from(request)));
    }

    @Operation(summary = "시즌 전환 예약")
    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED")
    @ApiResponse(responseCode = "403", description = "FORBIDDEN")
    @ApiResponse(responseCode = "409", description = "SEASON_RESERVATION_ALREADY_EXISTS")
    @ApiResponse(responseCode = "422", description = "INVALID_SEASON_PERIOD, INVALID_SEASON_RESERVATION")
    @PostMapping("/admin/season/reservation")
    public SeasonReservationResponse reserveSeason(@Valid @RequestBody SeasonChangeRequest request) {
        return SeasonReservationResponse.from(seasonReservationService.reserve(SeasonChangeCommand.from(request), Instant.now()));
    }

    @Operation(summary = "시즌 전환 예약 취소")
    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED")
    @ApiResponse(responseCode = "403", description = "FORBIDDEN")
    @ApiResponse(responseCode = "404", description = "SEASON_RESERVATION_NOT_FOUND")
    @DeleteMapping("/admin/season/reservation")
    public void cancelSeasonReservation() {
        seasonReservationService.cancel();
    }

    @Operation(summary = "시즌 풀 재배정")
    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED")
    @ApiResponse(responseCode = "403", description = "FORBIDDEN")
    @PostMapping("/admin/season/pools/reassign")
    public SeasonPoolReassignResponse reassignPools() {
        return SeasonPoolReassignResponse.from(seasonService.reassignPools());
    }
}
