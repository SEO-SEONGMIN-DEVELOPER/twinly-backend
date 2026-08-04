package com.nidus.twinly.season.controller;

import com.nidus.twinly.season.dto.command.SeasonChangeCommand;
import com.nidus.twinly.season.dto.request.SeasonChangeRequest;
import com.nidus.twinly.season.dto.response.SeasonChangeResponse;
import com.nidus.twinly.season.service.SeasonService;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SeasonAdminController {

    private final SeasonService seasonService;

    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED")
    @ApiResponse(responseCode = "403", description = "FORBIDDEN")
    @ApiResponse(responseCode = "422", description = "INVALID_SEASON_PERIOD")
    @PostMapping("/admin/season")
    public SeasonChangeResponse changeSeason(@Valid @RequestBody SeasonChangeRequest request) {
        return SeasonChangeResponse.from(seasonService.changeSeason(SeasonChangeCommand.from(request)));
    }
}
