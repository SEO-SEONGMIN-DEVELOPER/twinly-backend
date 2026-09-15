package com.nidus.twinly.common.domain;

public enum NationalInfo {
    DOMESTIC("0"),
    FOREIGN("1");

    private final String niceCode;

    NationalInfo(String niceCode) {
        this.niceCode = niceCode;
    }

    public static NationalInfo fromNiceCode(String code) {
        for (NationalInfo value : values()) {
            if (value.niceCode.equals(code)) {
                return value;
            }
        }

        return null;
    }
}
