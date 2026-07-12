package com.nidus.twinly.anon.dto.response;

import com.nidus.twinly.anon.dto.result.AnonStartResult;

import java.time.Instant;
import java.util.UUID;

public record AnonStartResponse(
        UUID anonSessionToken,
        Instant expiresAt
) {

    public static AnonStartResponse from(AnonStartResult result) {
        return new AnonStartResponse(result.anonSessionToken(), result.expiresAt());
    }
}