package com.nidus.twinly.report.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import com.nidus.twinly.report.domain.ReportReason;

public record ReportUserRequest(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @NotNull Long targetUserId,

        @NotNull ReportReason reason,
        @Schema(nullable = true)
        String detail
) {
}
