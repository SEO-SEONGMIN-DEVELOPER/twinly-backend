package com.nidus.twinly.me.dto.response;

import com.nidus.twinly.me.dto.result.MeStatusResult;

public record MeStatusResponse(
        MeStatusWithdrawalResponse withdrawal,
        MeStatusReportResponse report
) {

    public static MeStatusResponse from(MeStatusResult result) {
        return new MeStatusResponse(
                MeStatusWithdrawalResponse.from(result.withdrawal()),
                MeStatusReportResponse.from(result.report())
        );
    }
}
