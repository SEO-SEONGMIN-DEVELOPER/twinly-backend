package com.nidus.twinly.me.dto.response;

import com.nidus.twinly.me.dto.result.MeStatusSchoolVerificationResult;
import com.nidus.twinly.schoolverification.domain.SchoolVerificationMethod;
import com.nidus.twinly.schoolverification.domain.SchoolVerificationStatus;
import com.nidus.twinly.schoolverification.domain.StudentIdRejectReason;
import io.swagger.v3.oas.annotations.media.Schema;

public record MeStatusSchoolVerificationResponse(
        SchoolVerificationStatus status,
        SchoolVerificationMethod method,
        @Schema(nullable = true, description = "status 가 rejected 일 때만 값이 있다.")
        StudentIdRejectReason rejectReason
) {

    public static MeStatusSchoolVerificationResponse from(MeStatusSchoolVerificationResult result) {
        return new MeStatusSchoolVerificationResponse(result.status(), result.method(), result.rejectReason());
    }
}
