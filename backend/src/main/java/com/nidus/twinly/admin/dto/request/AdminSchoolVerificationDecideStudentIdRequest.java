package com.nidus.twinly.admin.dto.request;

import com.nidus.twinly.schoolverification.domain.StudentIdDecision;
import com.nidus.twinly.schoolverification.domain.StudentIdRejectReason;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminSchoolVerificationDecideStudentIdRequest(
        @NotNull StudentIdDecision decision,
        @Schema(nullable = true, description = "decision 이 REJECTED 일 때만 필수")
        StudentIdRejectReason rejectReason,
        @NotBlank @Size(max = 100) String reviewer
) {
}
