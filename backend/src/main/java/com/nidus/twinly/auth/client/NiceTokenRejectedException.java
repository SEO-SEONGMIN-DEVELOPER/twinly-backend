package com.nidus.twinly.auth.client;

import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;

public class NiceTokenRejectedException extends BusinessException {

    public NiceTokenRejectedException(String resultCode) {
        super(ErrorCode.IDENTITY_VERIFICATION_FAILED, "NICE 접근 토큰이 거절되었습니다. resultCode=" + resultCode);
    }
}
