package com.nidus.twinly.report.dto.request;

import com.nidus.twinly.report.domain.ReportReason;

public record ReportRequest(
        ReportReason reason,
        String detail
) {
}
