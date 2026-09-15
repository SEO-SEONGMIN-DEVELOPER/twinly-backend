package com.nidus.twinly.auth.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NiceAuthResultBody(
        @JsonProperty("result_code")
        String resultCode,
        @JsonProperty("result_message")
        String resultMessage,
        @JsonProperty("enc_data")
        String encData,
        @JsonProperty("integrity_value")
        String integrityValue
) {
}
