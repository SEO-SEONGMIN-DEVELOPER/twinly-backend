package com.nidus.twinly.auth.domain;

public enum IdentityVerificationResult {
    ISSUED,
    VERIFIED,
    AGE_NOT_ALLOWED,
    ALREADY_REGISTERED,
    INVALID_RESULT
}
