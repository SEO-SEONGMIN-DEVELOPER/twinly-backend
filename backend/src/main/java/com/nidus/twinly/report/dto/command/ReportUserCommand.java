package com.nidus.twinly.report.dto.command;

import com.nidus.twinly.report.domain.ReportReason;
import com.nidus.twinly.report.dto.request.ReportUserRequest;

public record ReportUserCommand(
        Long targetUserId,
        ReportReason reason,
        String detail
) {

    public static ReportUserCommand from(ReportUserRequest request) {
        return new ReportUserCommand(request.targetUserId(), request.reason(), request.detail());
    }
}
