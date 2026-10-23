package com.nidus.twinly.admin.dto.result;

import com.nidus.twinly.schoolverification.domain.StudentIdRejectReason;
import com.nidus.twinly.schoolverification.domain.StudentIdSubmissionStatus;

import java.time.Instant;
import java.util.UUID;

public record AdminSchoolVerificationStudentIdDetailHistoryItemResult(
        UUID submissionId,
        StudentIdSubmissionStatus status,
        StudentIdRejectReason rejectReason,
        String reviewer,
        Instant submittedAt,
        Instant decidedAt
) {
}
