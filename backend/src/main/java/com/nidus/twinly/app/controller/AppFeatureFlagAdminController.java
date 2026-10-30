package com.nidus.twinly.app.controller;

import com.nidus.twinly.app.dto.command.DotWorldFlagUpdateCommand;
import com.nidus.twinly.app.dto.request.DotWorldFlagUpdateRequest;
import com.nidus.twinly.app.dto.response.AppFeatureFlagsResponse;
import com.nidus.twinly.app.service.AppFeatureFlagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "앱 기능 스위치 (관리자)")
@RestController
@RequiredArgsConstructor
public class AppFeatureFlagAdminController {

    private final AppFeatureFlagService appFeatureFlagService;

    @Operation(summary = "앱 기능 스위치 조회")
    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED")
    @ApiResponse(responseCode = "403", description = "FORBIDDEN")
    @GetMapping("/admin/app/feature-flags")
    public AppFeatureFlagsResponse featureFlags() {
        return new AppFeatureFlagsResponse(appFeatureFlagService.dotWorldEnabled());
    }

    @Operation(summary = "도트 세계 스위치 변경", description = "서버마다 최대 5초 뒤에 반영된다.")
    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED")
    @ApiResponse(responseCode = "403", description = "FORBIDDEN")
    @PutMapping("/admin/app/feature-flags/dot-world")
    public void updateDotWorld(@Valid @RequestBody DotWorldFlagUpdateRequest request) {
        appFeatureFlagService.updateDotWorld(DotWorldFlagUpdateCommand.from(request));
    }
}
