package com.nidus.twinly.anon.dto.result;

import java.util.UUID;

public record AnonStartResult(UUID anonSessionToken, long expiresInSec) {
}