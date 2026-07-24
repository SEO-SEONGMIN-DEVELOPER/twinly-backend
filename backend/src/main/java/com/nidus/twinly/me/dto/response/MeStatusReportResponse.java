package com.nidus.twinly.me.dto.response;

import com.nidus.twinly.me.dto.result.MeStatusReportResult;

import java.util.List;

public record MeStatusReportResponse(
        Boolean isReported,
        List<String> reasons
) {

    public static MeStatusReportResponse from(MeStatusReportResult result) {
        return new MeStatusReportResponse(
                result.isReported(),
                result.reasons()
        );
    }
}
