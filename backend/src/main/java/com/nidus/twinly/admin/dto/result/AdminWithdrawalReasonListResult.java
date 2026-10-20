package com.nidus.twinly.admin.dto.result;

import java.util.List;

public record AdminWithdrawalReasonListResult(
        long withdrawalCount,
        List<ReasonCount> reasonCounts,
        AdminPageResult<AdminFeedbackResult> page
) {

    public record ReasonCount(
            String label,
            long count
    ) {
    }
}
