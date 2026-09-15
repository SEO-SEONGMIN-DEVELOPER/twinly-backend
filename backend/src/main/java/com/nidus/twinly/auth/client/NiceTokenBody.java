package com.nidus.twinly.auth.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NiceTokenBody(
        @JsonProperty("result_code")
        String resultCode,
        @JsonProperty("result_message")
        String resultMessage,
        @JsonProperty("access_token")
        String accessToken,
        @JsonProperty("expires_in")
        Long expiresIn,
        @JsonProperty("iterators")
        Integer iterators,
        @JsonProperty("ticket")
        String ticket
) {
}
