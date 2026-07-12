package com.nidus.twinly.auth.dto.request;

import java.util.UUID;

public record AuthEmailVerifyRequest(
        UUID emailVerificationToken,
        String code
) {
}
