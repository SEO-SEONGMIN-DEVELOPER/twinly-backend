package com.nidus.twinly.anon.dto;

import java.util.UUID;

public record AnonStartResult(UUID anonSessionToken, long expiresInSec) {
}