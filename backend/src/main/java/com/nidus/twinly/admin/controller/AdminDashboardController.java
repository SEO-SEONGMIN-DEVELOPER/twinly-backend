package com.nidus.twinly.admin.controller;

import com.nidus.twinly.admin.dto.response.AdminDashboardResponse;
import com.nidus.twinly.admin.service.AdminDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "어드민 콘솔 (관리자)")
@RestController
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @Operation(summary = "대시보드", description = "가입자·구독자·탈퇴자 수와 성별, 학교별 가입자 수")
    @GetMapping("/admin/dashboard")
    public AdminDashboardResponse dashboard() {
        return AdminDashboardResponse.from(adminDashboardService.dashboard());
    }
}
