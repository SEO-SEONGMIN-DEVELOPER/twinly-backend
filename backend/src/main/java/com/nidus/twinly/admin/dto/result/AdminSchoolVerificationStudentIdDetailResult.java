package com.nidus.twinly.admin.dto.result;

import com.nidus.twinly.schoolverification.domain.StudentIdRejectReason;
import com.nidus.twinly.schoolverification.domain.StudentIdSubmissionStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminSchoolVerificationStudentIdDetailResult(
        UUID submissionId,
        Long userId,
        String nickname,
        StudentIdSubmissionStatus status,
        StudentIdRejectReason rejectReason,
        String reviewer,
        String photoUrl,
        String identityName,
        String organization,
        Instant submittedAt,
        Instant decidedAt,
        List<AdminSchoolVerificationStudentIdDetailHistoryItemResult> history
) {
}
