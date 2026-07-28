package com.nidus.twinly.report.dto.response;

import com.nidus.twinly.report.dto.result.ReportUserResult;

public record ReportUserResponse(
        Boolean autoBlock
) {

    public static ReportUserResponse from(ReportUserResult result) {
        return new ReportUserResponse(result.autoBlock());
    }
}
