package com.nidus.twinly.admin.dto.result;

import java.time.Instant;
import java.util.UUID;

public record AdminSchoolVerificationStudentIdsItemResult(
        UUID submissionId,
        Long userId,
        String nickname,
        String organization,
        Instant submittedAt,
        Long elapsedMinutes
) {
}
