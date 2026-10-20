package com.nidus.twinly.admin.controller;

import com.nidus.twinly.admin.dto.response.AdminUsageResponse;
import com.nidus.twinly.admin.service.AdminUsageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "어드민 콘솔 (관리자)")
@RestController
@RequiredArgsConstructor
public class AdminUsageController {

    private final AdminUsageService adminUsageService;

    @Operation(summary = "기능 이용 현황", description = "밸런스 게임 이용자와 채팅방 입장 현황")
    @GetMapping("/admin/usage")
    public AdminUsageResponse usage() {
        return AdminUsageResponse.from(adminUsageService.usage());
    }
}
