package com.nidus.twinly.admin.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.admin.dto.result.AdminSchoolVerificationStudentIdsItemResult;

import java.time.Instant;
import java.util.UUID;

public record AdminSchoolVerificationStudentIdsItemResponse(
        UUID submissionId,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long userId,
        String nickname,
        String organization,
        Instant submittedAt,
        Long elapsedMinutes
) {

    public static AdminSchoolVerificationStudentIdsItemResponse from(AdminSchoolVerificationStudentIdsItemResult result) {
        return new AdminSchoolVerificationStudentIdsItemResponse(
                result.submissionId(),
                result.userId(),
                result.nickname(),
                result.organization(),
                result.submittedAt(),
                result.elapsedMinutes()
        );
    }
}
