package com.nidus.twinly.auth.dto.request;

import java.util.UUID;

public record AuthLoginRequest(
        UUID smsVerifiedToken
) {
}
