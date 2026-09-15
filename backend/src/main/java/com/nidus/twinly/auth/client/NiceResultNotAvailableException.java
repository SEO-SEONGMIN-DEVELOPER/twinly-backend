package com.nidus.twinly.auth.client;

import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;

public class NiceResultNotAvailableException extends BusinessException {

    public NiceResultNotAvailableException(String resultCode) {
        super(ErrorCode.IDENTITY_NOT_VERIFIED, "NICE 인증 결과를 제공받을 수 없습니다. resultCode=" + resultCode);
    }
}
