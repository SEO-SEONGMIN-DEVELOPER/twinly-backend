package com.nidus.twinly.me.dto.result;

import com.nidus.twinly.schoolverification.domain.SchoolVerificationMethod;
import com.nidus.twinly.schoolverification.domain.SchoolVerificationStatus;
import com.nidus.twinly.schoolverification.domain.StudentIdRejectReason;

public record MeStatusSchoolVerificationResult(
        SchoolVerificationStatus status,
        SchoolVerificationMethod method,
        StudentIdRejectReason rejectReason
) {
}
