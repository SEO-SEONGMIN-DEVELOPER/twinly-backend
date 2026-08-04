package com.nidus.twinly.report.controller;

import jakarta.validation.Valid;
import com.nidus.twinly.report.dto.command.ReportAiUtteranceCommand;
import com.nidus.twinly.report.dto.command.ReportUserCommand;
import com.nidus.twinly.report.dto.request.ReportAiUtteranceRequest;
import com.nidus.twinly.report.dto.request.ReportUserRequest;
import com.nidus.twinly.report.dto.response.ReportUserResponse;
import com.nidus.twinly.report.service.ReportService;
import com.nidus.twinly.user.dto.header.UserInfo;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @ApiResponse(responseCode = "422", description = "CANNOT_REPORT_SELF")
    @PostMapping("/api/v1/reports/users")
    public ReportUserResponse reportUser(@AuthenticationPrincipal UserInfo userInfo,
                                         @Valid @RequestBody ReportUserRequest request) {
        return ReportUserResponse.from(reportService.reportUser(userInfo.id(), ReportUserCommand.from(request)));
    }

    @ApiResponses({
            @ApiResponse(responseCode = "403", description = "NOT_SCENE_OWNER"),
            @ApiResponse(responseCode = "404", description = "SCENE_NOT_FOUND"),
            @ApiResponse(responseCode = "422", description = "SCENE_TARGET_MISMATCH")
    })
    @PostMapping("/api/v1/reports/ai-utterances")
    public void reportAiUtterance(@AuthenticationPrincipal UserInfo userInfo,
                                  @Valid @RequestBody ReportAiUtteranceRequest request) {
        reportService.reportAiUtterance(userInfo.id(), ReportAiUtteranceCommand.from(request));
    }
}
