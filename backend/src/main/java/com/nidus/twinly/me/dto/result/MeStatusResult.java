package com.nidus.twinly.me.dto.result;

public record MeStatusResult(
        MeStatusWithdrawalResult withdrawal,
        MeStatusReportResult report
) {
}
