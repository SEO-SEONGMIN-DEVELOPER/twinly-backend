package com.nidus.twinly.report.dto.request;

import jakarta.validation.constraints.NotNull;
import com.nidus.twinly.report.domain.ReportReason;

public record ReportRequest(
        @NotNull ReportReason reason,
        @NotNull String detail
) {
}
