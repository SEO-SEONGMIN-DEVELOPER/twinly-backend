package com.nidus.twinly.app.controller;

import com.nidus.twinly.app.domain.AppPlatform;
import com.nidus.twinly.app.dto.command.AppVersionPolicyUpdateCommand;
import com.nidus.twinly.app.dto.command.MaintenanceUpdateCommand;
import com.nidus.twinly.app.dto.request.AppVersionPolicyUpdateRequest;
import com.nidus.twinly.app.dto.request.MaintenanceUpdateRequest;
import com.nidus.twinly.app.dto.response.AppBlockPolicyResponse;
import com.nidus.twinly.app.service.AppBlockPolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "앱 차단 (관리자)")
@RestController
@RequiredArgsConstructor
public class AppAdminController {

    private final AppBlockPolicyService appBlockPolicyService;

    @Operation(summary = "앱 차단 정책 조회 (점검 상태·플랫폼별 최소 버전)")
    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED")
    @ApiResponse(responseCode = "403", description = "FORBIDDEN")
    @GetMapping("/admin/app/block-policy")
    public AppBlockPolicyResponse blockPolicy() {
        return AppBlockPolicyResponse.from(appBlockPolicyService.current());
    }

    @Operation(summary = "점검 상태 변경")
    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED")
    @ApiResponse(responseCode = "403", description = "FORBIDDEN")
    @PutMapping("/admin/app/maintenance")
    public void updateMaintenance(@Valid @RequestBody MaintenanceUpdateRequest request) {
        appBlockPolicyService.updateMaintenance(MaintenanceUpdateCommand.from(request));
    }

    @Operation(summary = "플랫폼별 최소 버전·스토어 URL 변경")
    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED")
    @ApiResponse(responseCode = "403", description = "FORBIDDEN")
    @PutMapping("/admin/app/version-policies/{platform}")
    public void updateVersionPolicy(@PathVariable AppPlatform platform,
                                    @Valid @RequestBody AppVersionPolicyUpdateRequest request) {
        appBlockPolicyService.updateVersionPolicy(AppVersionPolicyUpdateCommand.from(platform, request));
    }
}
