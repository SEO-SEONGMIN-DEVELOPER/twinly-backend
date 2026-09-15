package com.nidus.twinly.auth.dto.result;

public record NiceAuthUrlResult(
        String authUrl,
        String transactionId,
        String returnUrl,
        String closeUrl
) {
}
