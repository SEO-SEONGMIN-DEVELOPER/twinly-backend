package com.nidus.twinly.auth.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NiceAuthResult(
        @JsonProperty("name")
        String name,
        @JsonProperty("birthdate")
        String birthdate,
        @JsonProperty("gender")
        String gender,
        @JsonProperty("national_info")
        String nationalInfo,
        @JsonProperty("di")
        String di,
        @JsonProperty("mobile_co")
        String mobileCo,
        @JsonProperty("mobile_no")
        String mobileNo
) {
}
