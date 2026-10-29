package com.nidus.twinly.common.web;

import lombok.Getter;
import org.springframework.http.HttpHeaders;

@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final HttpHeaders headers;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.headers = new HttpHeaders();
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.headers = new HttpHeaders();
    }

    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getDefaultMessage(), cause);
        this.errorCode = errorCode;
        this.headers = new HttpHeaders();
    }

    public BusinessException(ErrorCode errorCode, HttpHeaders headers) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.headers = headers;
    }
}
