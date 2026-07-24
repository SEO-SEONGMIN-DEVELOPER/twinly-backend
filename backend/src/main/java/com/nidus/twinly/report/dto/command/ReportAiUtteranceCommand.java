package com.nidus.twinly.report.dto.command;

import com.nidus.twinly.report.dto.request.ReportAiUtteranceRequest;

public record ReportAiUtteranceCommand(
        Long targetUserId,
        Long sceneId,
        String utteranceText,
        String reason
) {

    public static ReportAiUtteranceCommand from(ReportAiUtteranceRequest request) {
        return new ReportAiUtteranceCommand(request.targetUserId(), request.sceneId(), request.utteranceText(), request.reason());
    }
}
