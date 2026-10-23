package com.nidus.twinly.schoolverification.event;

import java.util.UUID;

public record StudentIdReviewRequestedEvent(
        Long userId,
        UUID submissionId,
        String organization,
        boolean resubmitted
) {
}
