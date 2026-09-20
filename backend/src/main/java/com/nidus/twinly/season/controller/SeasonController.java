package com.nidus.twinly.season.controller;

import com.nidus.twinly.season.dto.response.SeasonParticipationResponse;
import com.nidus.twinly.season.service.SeasonService;
import com.nidus.twinly.user.dto.header.UserInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "시즌")
@RestController
@RequiredArgsConstructor
public class SeasonController {

    private final SeasonService seasonService;

    @Operation(summary = "시즌 참여", description = "구매 상태를 동기화한 뒤 결제와 필수 약관 동의를 만족하면 현재 시즌에 참여시킨다. 이미 참여했으면 최초 참여 시각을 유지한다.")
    @ApiResponse(responseCode = "403", description = "SIMULATION_ACCESS_REQUIRED, SIMULATION_CONSENT_REQUIRED")
    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND")
    @PutMapping("/api/v1/season/participation")
    public void participateIn(@AuthenticationPrincipal UserInfo userInfo) {
        seasonService.participateIn(userInfo.id());
    }

    @Operation(summary = "시즌 참여 상태 조회")
    @GetMapping("/api/v1/season/participation")
    public SeasonParticipationResponse participation(@AuthenticationPrincipal UserInfo userInfo) {
        return SeasonParticipationResponse.from(seasonService.participation(userInfo.id()));
    }
}
