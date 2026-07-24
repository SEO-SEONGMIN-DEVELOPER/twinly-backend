package com.nidus.twinly.me.dto.result;

import java.util.List;

public record MeStatusReportResult(
        Boolean isReported,
        List<String> reasons
) {
}
