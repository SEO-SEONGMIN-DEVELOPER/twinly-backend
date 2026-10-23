package com.nidus.twinly.admin.dto.response;

import com.nidus.twinly.admin.dto.result.AdminSchoolVerificationStudentIdDetailHistoryItemResult;
import com.nidus.twinly.schoolverification.domain.StudentIdRejectReason;
import com.nidus.twinly.schoolverification.domain.StudentIdSubmissionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record AdminSchoolVerificationStudentIdDetailHistoryItemResponse(
        UUID submissionId,
        StudentIdSubmissionStatus status,
        @Schema(nullable = true)
        StudentIdRejectReason rejectReason,
        @Schema(nullable = true)
        String reviewer,
        Instant submittedAt,
        @Schema(nullable = true)
        Instant decidedAt
) {

    public static AdminSchoolVerificationStudentIdDetailHistoryItemResponse from(AdminSchoolVerificationStudentIdDetailHistoryItemResult result) {
        return new AdminSchoolVerificationStudentIdDetailHistoryItemResponse(
                result.submissionId(),
                result.status(),
                result.rejectReason(),
                result.reviewer(),
                result.submittedAt(),
                result.decidedAt()
        );
    }
}
