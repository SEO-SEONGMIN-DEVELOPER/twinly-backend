package com.nidus.twinly.common.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum Gender {
    @JsonProperty("male") MALE,
    @JsonProperty("female") FEMALE;

    private static final String NICE_CODE_FEMALE = "0";
    private static final String NICE_CODE_MALE = "1";

    public static Gender fromNiceCode(String code) {
        if (NICE_CODE_MALE.equals(code)) {
            return MALE;
        }

        if (NICE_CODE_FEMALE.equals(code)) {
            return FEMALE;
        }

        return null;
    }
}