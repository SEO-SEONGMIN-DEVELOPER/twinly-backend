package com.nidus.twinly.common.domain;

public enum MobileCarrier {
    SKT("1"),
    KT("2"),
    LGU("3"),
    SKT_MVNO("5"),
    KT_MVNO("6"),
    LGU_MVNO("7");

    private final String niceCode;

    MobileCarrier(String niceCode) {
        this.niceCode = niceCode;
    }

    public static MobileCarrier fromNiceCode(String code) {
        for (MobileCarrier value : values()) {
            if (value.niceCode.equals(code)) {
                return value;
            }
        }

        return null;
    }
}
