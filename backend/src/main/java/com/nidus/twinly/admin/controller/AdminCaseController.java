package com.nidus.twinly.admin.controller;

import com.nidus.twinly.admin.domain.CaseStatusFilter;
import com.nidus.twinly.admin.dto.command.AdminAiUtteranceReportReviewCommand;
import com.nidus.twinly.admin.dto.command.AdminSuggestionReviewCommand;
import com.nidus.twinly.admin.dto.command.AdminUserReportReviewCommand;
import com.nidus.twinly.admin.dto.request.AdminAiUtteranceReportReviewRequest;
import com.nidus.twinly.admin.dto.request.AdminSuggestionReviewRequest;
import com.nidus.twinly.admin.dto.request.AdminUserReportReviewRequest;
import com.nidus.twinly.admin.dto.response.AdminAiUtteranceReportResponse;
import com.nidus.twinly.admin.dto.response.AdminCaseSummaryResponse;
import com.nidus.twinly.admin.dto.response.AdminFeedbackResponse;
import com.nidus.twinly.admin.dto.response.AdminPageResponse;
import com.nidus.twinly.admin.dto.response.AdminUserReportResponse;
import com.nidus.twinly.admin.dto.response.AdminWithdrawalReasonListResponse;
import com.nidus.twinly.admin.service.AdminCaseService;
import com.nidus.twinly.common.web.RequestId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "어드민 콘솔 (관리자)")
@Validated
@RestController
@RequiredArgsConstructor
public class AdminCaseController {

    private final AdminCaseService adminCaseService;

    @Operation(summary = "미처리 신고·건의 요약")
    @GetMapping("/admin/cases/summary")
    public AdminCaseSummaryResponse summary() {
        return AdminCaseSummaryResponse.from(adminCaseService.summary());
    }

    @Operation(summary = "유저 신고 목록", description = "status 없으면 전체. DONE은 조치·반려 모두")
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST")
    @GetMapping("/admin/cases/user-reports")
    public AdminPageResponse<AdminUserReportResponse> userReports(@RequestParam(required = false) CaseStatusFilter status,
                                                                  @RequestParam(defaultValue = "0") @Min(0) Integer page,
                                                                  @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer size) {
        return AdminPageResponse.from(adminCaseService.userReports(status, page, size), AdminUserReportResponse::from);
    }

    @Operation(summary = "유저 신고 처리", description = "RESOLVED·REJECTED로 바꾸면 처리 시각이 기록되고, 다시 PENDING·IN_REVIEW로 되돌리면 지워진다.")
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST")
    @ApiResponse(responseCode = "404", description = "REPORT_NOT_FOUND")
    @PatchMapping("/admin/cases/user-reports/{reportId}")
    public void reviewUserReport(@PathVariable String reportId, @Valid @RequestBody AdminUserReportReviewRequest request) {
        adminCaseService.reviewUserReport(new AdminUserReportReviewCommand(
                RequestId.toLong(reportId, "reportId"), request.status(), request.reviewNote(), request.actionNote()));
    }

    @Operation(summary = "AI 발화 신고 목록", description = "status 없으면 전체. DONE은 조치·반려 모두")
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST")
    @GetMapping("/admin/cases/ai-utterance-reports")
    public AdminPageResponse<AdminAiUtteranceReportResponse> aiUtteranceReports(@RequestParam(required = false) CaseStatusFilter status,
                                                                                @RequestParam(defaultValue = "0") @Min(0) Integer page,
                                                                                @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer size) {
        return AdminPageResponse.from(adminCaseService.aiUtteranceReports(status, page, size), AdminAiUtteranceReportResponse::from);
    }

    @Operation(summary = "AI 발화 신고 처리")
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST")
    @ApiResponse(responseCode = "404", description = "REPORT_NOT_FOUND")
    @PatchMapping("/admin/cases/ai-utterance-reports/{reportId}")
    public void reviewAiUtteranceReport(@PathVariable String reportId, @Valid @RequestBody AdminAiUtteranceReportReviewRequest request) {
        adminCaseService.reviewAiUtteranceReport(new AdminAiUtteranceReportReviewCommand(
                RequestId.toLong(reportId, "reportId"), request.status(), request.violationKind(), request.reviewNote(), request.actionNote()));
    }

    @Operation(summary = "건의사항 목록", description = "status 없으면 전체. PENDING은 접수(RECEIVED)")
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST")
    @GetMapping("/admin/cases/suggestions")
    public AdminPageResponse<AdminFeedbackResponse> suggestions(@RequestParam(required = false) CaseStatusFilter status,
                                                                @RequestParam(defaultValue = "0") @Min(0) Integer page,
                                                                @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer size) {
        return AdminPageResponse.from(adminCaseService.suggestions(status, page, size), AdminFeedbackResponse::from);
    }

    @Operation(summary = "건의사항 검토 상태·메모 저장")
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST")
    @ApiResponse(responseCode = "404", description = "FEEDBACK_NOT_FOUND")
    @PatchMapping("/admin/cases/suggestions/{feedbackId}")
    public void reviewSuggestion(@PathVariable String feedbackId, @Valid @RequestBody AdminSuggestionReviewRequest request) {
        adminCaseService.reviewSuggestion(new AdminSuggestionReviewCommand(
                RequestId.toLong(feedbackId, "feedbackId"), request.status(), request.memo()));
    }

    @Operation(summary = "탈퇴 사유", description = "선택지별 집계와 최근 탈퇴 사유 목록")
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST")
    @GetMapping("/admin/cases/withdrawal-reasons")
    public AdminWithdrawalReasonListResponse withdrawalReasons(@RequestParam(defaultValue = "0") @Min(0) Integer page,
                                                               @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer size) {
        return AdminWithdrawalReasonListResponse.from(adminCaseService.withdrawalReasons(page, size));
    }
}
