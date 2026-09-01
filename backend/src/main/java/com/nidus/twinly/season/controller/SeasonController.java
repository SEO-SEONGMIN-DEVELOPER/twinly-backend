package com.nidus.twinly.season.controller;

import com.nidus.twinly.season.dto.response.SeasonParticipationResponse;
import com.nidus.twinly.season.service.SeasonService;
import com.nidus.twinly.user.dto.header.UserInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "시즌")
@RestController
@RequiredArgsConstructor
public class SeasonController {

    private final SeasonService seasonService;

    @Operation(summary = "시즌 참여 상태 조회")
    @GetMapping("/api/v1/season/participation")
    public SeasonParticipationResponse participation(@AuthenticationPrincipal UserInfo userInfo) {
        return SeasonParticipationResponse.from(seasonService.participation(userInfo.id()));
    }
}
