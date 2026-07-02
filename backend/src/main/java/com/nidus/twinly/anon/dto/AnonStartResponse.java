package com.nidus.twinly.anon.dto;

import java.util.UUID;

public record AnonStartResponse(UUID anonSessionToken, long expiresInSec) {
    public static AnonStartResponse from(AnonStartResult result) {
        return new AnonStartResponse(result.anonSessionToken(), result.expiresInSec());
    }
}