package com.nidus.twinly.report.dto.command;

import com.nidus.twinly.report.domain.ReportReason;
import com.nidus.twinly.report.dto.request.ReportRequest;

public record ReportCommand(
        ReportReason reason,
        String detail
) {

    public static ReportCommand from(ReportRequest request) {
        return new ReportCommand(request.reason(), request.detail());
    }
}
