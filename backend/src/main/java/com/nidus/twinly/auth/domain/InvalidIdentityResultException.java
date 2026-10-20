package com.nidus.twinly.auth.domain;

import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import lombok.Getter;

@Getter
public class InvalidIdentityResultException extends BusinessException {

    private final String di;

    public InvalidIdentityResultException(String message, String di) {
        super(ErrorCode.IDENTITY_VERIFICATION_FAILED, message);
        this.di = di;
    }
}
