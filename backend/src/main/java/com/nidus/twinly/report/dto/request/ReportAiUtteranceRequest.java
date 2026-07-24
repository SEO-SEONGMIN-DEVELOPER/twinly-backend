package com.nidus.twinly.report.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReportAiUtteranceRequest(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @NotNull Long targetUserId,

        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @NotNull Long sceneId,

        @NotBlank String utteranceText,
        @NotNull String reason
) {
}
