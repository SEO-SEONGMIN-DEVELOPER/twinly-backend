package com.nidus.twinly.auth.dto.result;

import java.util.UUID;

public record AuthEmailVerifyResult(
        UUID emailVerifiedToken
) {
}
