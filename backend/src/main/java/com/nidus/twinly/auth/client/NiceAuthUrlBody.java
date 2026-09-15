package com.nidus.twinly.auth.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NiceAuthUrlBody(
        @JsonProperty("result_code")
        String resultCode,
        @JsonProperty("result_message")
        String resultMessage,
        @JsonProperty("auth_url")
        String authUrl,
        @JsonProperty("transaction_id")
        String transactionId
) {
}
