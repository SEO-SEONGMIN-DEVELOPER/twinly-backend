package com.nidus.twinly.report.dto.response;

import com.nidus.twinly.report.dto.result.ReportResult;

public record ReportResponse(
        Boolean autoBlock
) {

    public static ReportResponse from(ReportResult result) {
        return new ReportResponse(result.autoBlock());
    }
}
