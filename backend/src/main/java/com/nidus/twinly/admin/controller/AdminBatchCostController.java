package com.nidus.twinly.admin.controller;

import com.nidus.twinly.admin.dto.response.AdminBatchCostResponse;
import com.nidus.twinly.admin.service.AdminBatchCostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "어드민 콘솔 (관리자)")
@RestController
@RequiredArgsConstructor
public class AdminBatchCostController {

    private final AdminBatchCostService adminBatchCostService;

    @Operation(summary = "하루 배치 비용·세계 시간", description = "AI 서버가 집계한 하루 배치의 그룹별·전체 비용과 그룹별 세계 시간. AI 서버 조회 실패 시 502")
    @GetMapping("/admin/simulation/batch-costs")
    public AdminBatchCostResponse batchCosts() {
        return AdminBatchCostResponse.from(adminBatchCostService.batchCosts());
    }
}
