package com.nidus.twinly.admin.controller;

import com.nidus.twinly.admin.dto.response.AdminSurveyResponse;
import com.nidus.twinly.admin.service.AdminSurveyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "어드민 콘솔 (관리자)")
@RestController
@RequiredArgsConstructor
public class AdminSurveyController {

    private final AdminSurveyService adminSurveyService;

    @Operation(summary = "설문 통계", description = "온보딩 성향 설문의 문항별·선택지별·성별 응답 수")
    @GetMapping("/admin/survey")
    public AdminSurveyResponse survey() {
        return AdminSurveyResponse.from(adminSurveyService.survey());
    }
}
