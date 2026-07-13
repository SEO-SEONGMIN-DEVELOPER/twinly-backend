package com.nidus.twinly.auth.domain;

import java.util.UUID;

public record VerifiedToken(
        UUID smsVerifiedToken,
        UUID emailVerifiedToken
) {
}