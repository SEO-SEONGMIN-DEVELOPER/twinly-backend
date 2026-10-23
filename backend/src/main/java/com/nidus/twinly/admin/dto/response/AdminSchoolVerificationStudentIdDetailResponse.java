package com.nidus.twinly.admin.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.admin.dto.result.AdminSchoolVerificationStudentIdDetailResult;
import com.nidus.twinly.schoolverification.domain.StudentIdRejectReason;
import com.nidus.twinly.schoolverification.domain.StudentIdSubmissionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminSchoolVerificationStudentIdDetailResponse(
        UUID submissionId,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long userId,
        String nickname,
        StudentIdSubmissionStatus status,
        @Schema(nullable = true)
        StudentIdRejectReason rejectReason,
        @Schema(nullable = true)
        String reviewer,
        @Schema(nullable = true, description = "검수 대기 중일 때만 주는 5분짜리 S3 주소. 판정 뒤에는 null 이다.")
        String photoUrl,
        String identityName,
        String organization,
        Instant submittedAt,
        @Schema(nullable = true)
        Instant decidedAt,
        List<AdminSchoolVerificationStudentIdDetailHistoryItemResponse> history
) {

    public static AdminSchoolVerificationStudentIdDetailResponse from(AdminSchoolVerificationStudentIdDetailResult result) {
        return new AdminSchoolVerificationStudentIdDetailResponse(
                result.submissionId(),
                result.userId(),
                result.nickname(),
                result.status(),
                result.rejectReason(),
                result.reviewer(),
                result.photoUrl(),
                result.identityName(),
                result.organization(),
                result.submittedAt(),
                result.decidedAt(),
                result.history().stream().map(AdminSchoolVerificationStudentIdDetailHistoryItemResponse::from).toList()
        );
    }
}
