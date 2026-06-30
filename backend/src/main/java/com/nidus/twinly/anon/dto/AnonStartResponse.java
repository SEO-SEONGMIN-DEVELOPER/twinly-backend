package com.nidus.twinly.anon.dto;

import java.util.UUID;

public record AnonStartResponse(UUID anonSessionToken, long expiresInSec) {
}