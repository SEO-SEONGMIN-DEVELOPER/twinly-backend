package com.nidus.twinly.auth.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NiceErrorBody(
        @JsonProperty("result_code")
        String resultCode,
        @JsonProperty("result_message")
        String resultMessage
) {
}
