package com.nidus.twinly.season.controller;

import com.nidus.twinly.season.dto.response.SeasonLatestRecapResponse;
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

    @Operation(summary = "시즌 참여", description = "학교 인증이 승인된 회원만 참여할 수 있다. 구매 상태를 동기화한 뒤 결제와 필수 약관 동의를 만족하면 현재 시즌에 참여시킨다. 이미 참여했으면 최초 참여 시각을 유지한다.")
    @ApiResponse(responseCode = "403", description = "SCHOOL_VERIFICATION_NOT_APPROVED, SIMULATION_ACCESS_REQUIRED, SIMULATION_CONSENT_REQUIRED")
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

    @Operation(summary = "최근 시즌 리캡 조회", description = "지금 제공 중인 본인의 가장 최근 시즌 리캡을 준다. 현재 시즌 참여 여부·구독 상태와 관계없이 조회할 수 있다. 상대의 이름·사진은 응답 시점 값이며, 탈퇴했거나 차단 관계인 상대는 빠진다.")
    @ApiResponse(responseCode = "404", description = "SEASON_RECAP_NOT_FOUND")
    @GetMapping("/api/v1/season/recaps/latest")
    public SeasonLatestRecapResponse latestRecap(@AuthenticationPrincipal UserInfo userInfo) {
        return SeasonLatestRecapResponse.from(seasonService.latestRecap(userInfo.id()));
    }
}
