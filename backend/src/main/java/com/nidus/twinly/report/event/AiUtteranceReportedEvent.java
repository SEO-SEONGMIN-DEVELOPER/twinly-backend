package com.nidus.twinly.report.event;

public record AiUtteranceReportedEvent(
        Long reportId,
        Long reporterUserId,
        Long reportedUserId,
        Long sceneId,
        String utteranceText,
        String reason
) {
}
