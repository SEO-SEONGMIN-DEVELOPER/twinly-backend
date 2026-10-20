package com.nidus.twinly.auth.dto.result;

public record IdentityAuthUrlResult(
        String authUrl,
        String transactionId,
        String returnUrl,
        String closeUrl
) {
}
