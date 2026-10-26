package com.nidus.twinly.admin.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.admin.dto.result.AdminSchoolVerificationStudentIdsItemResult;
import com.nidus.twinly.schoolverification.domain.StudentIdSubmissionStatus;

import java.time.Instant;
import java.util.UUID;

public record AdminSchoolVerificationStudentIdsItemResponse(
        UUID submissionId,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long userId,
        String nickname,
        String organization,
        StudentIdSubmissionStatus status,
        String reviewer,
        Instant submittedAt,
        Instant decidedAt,
        Long elapsedMinutes
) {

    public static AdminSchoolVerificationStudentIdsItemResponse from(AdminSchoolVerificationStudentIdsItemResult result) {
        return new AdminSchoolVerificationStudentIdsItemResponse(
                result.submissionId(),
                result.userId(),
                result.nickname(),
                result.organization(),
                result.status(),
                result.reviewer(),
                result.submittedAt(),
                result.decidedAt(),
                result.elapsedMinutes()
        );
    }
}
