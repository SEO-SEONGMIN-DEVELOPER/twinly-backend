package com.nidus.twinly.common.docs;

import com.nidus.twinly.common.web.ErrorCode;

public record ErrorCodeDoc(
        String code,
        int status,
        String message
) {
    public static ErrorCodeDoc of(ErrorCode errorCode) {
        return new ErrorCodeDoc(errorCode.name(), errorCode.getStatus().value(), errorCode.getDefaultMessage());
    }
}
