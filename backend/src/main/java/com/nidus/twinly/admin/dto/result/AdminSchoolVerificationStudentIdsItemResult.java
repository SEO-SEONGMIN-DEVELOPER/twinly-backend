package com.nidus.twinly.admin.dto.result;

import com.nidus.twinly.schoolverification.domain.StudentIdSubmissionStatus;

import java.time.Instant;
import java.util.UUID;

public record AdminSchoolVerificationStudentIdsItemResult(
        UUID submissionId,
        Long userId,
        String nickname,
        String organization,
        StudentIdSubmissionStatus status,
        String reviewer,
        Instant submittedAt,
        Instant decidedAt,
        Long elapsedMinutes
) {
}
