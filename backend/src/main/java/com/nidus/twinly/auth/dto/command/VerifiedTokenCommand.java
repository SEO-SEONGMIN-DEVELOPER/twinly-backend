package com.nidus.twinly.auth.dto.command;

import java.util.UUID;

public record VerifiedTokenCommand(
        UUID smsVerifiedToken,
        UUID emailVerifiedToken
) {
}