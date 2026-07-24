package com.nidus.twinly.report.controller;

import jakarta.validation.Valid;
import com.nidus.twinly.common.web.RequestId;
import com.nidus.twinly.report.dto.command.ReportAiUtteranceCommand;
import com.nidus.twinly.report.dto.command.ReportCommand;
import com.nidus.twinly.report.dto.request.ReportAiUtteranceRequest;
import com.nidus.twinly.report.dto.request.ReportRequest;
import com.nidus.twinly.report.dto.response.ReportResponse;
import com.nidus.twinly.report.service.ReportService;
import com.nidus.twinly.user.annotation.CurrentUser;
import com.nidus.twinly.user.dto.header.UserInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping("/api/v1/reports/{userId}")
    public ReportResponse report(@CurrentUser UserInfo userInfo,
                                 @PathVariable String userId,
                                 @Valid @RequestBody ReportRequest request) {
        return ReportResponse.from(reportService.report(userInfo.id(), RequestId.toLong(userId, "userId"), ReportCommand.from(request)));
    }

    @PostMapping("/api/v1/reports/ai-utterance")
    public void aiUtterance(@CurrentUser UserInfo userInfo,
                            @Valid @RequestBody ReportAiUtteranceRequest request) {
        reportService.aiUtterance(userInfo.id(), ReportAiUtteranceCommand.from(request));
    }
}
