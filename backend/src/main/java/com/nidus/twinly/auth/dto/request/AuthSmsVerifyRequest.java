package com.nidus.twinly.auth.dto.request;

import java.util.UUID;

public record AuthSmsVerifyRequest(
        UUID smsVerificationToken,
        String code
) {
}
