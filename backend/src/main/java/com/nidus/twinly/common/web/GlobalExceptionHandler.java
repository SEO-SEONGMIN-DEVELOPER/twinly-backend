package com.nidus.twinly.common.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /*
     * [멘토링 피드백]
     * - 에러 메시지 로그 어떻게 뜨는지 확인
     * - 세부 타입을 에러 코드 번호로 세분화해서 나중에 에러 볼 때 가독성 높이기
     */

    /*
     * [멘토링 피드백]
     * CI/CD: 앱 / 백엔드 / AI 별로 어떻게 구분해서 올릴지 방법 찾아보기
     */

    /*
     * [멘토링 피드백]
     * API 명세서 자동 생성을 AI가 검수하게끔 플로우 만들기
     */

    /*
     * [멘토링 피드백]
     * 테스트 서버 자동 스케일링 이용하기
     */

    /*
     * [멘토링 피드백]
     * iac, 테라폼, 플로미 배워서 적용해보기 (코드로 인프라 설정)
     */

    /*
     * [멘토링 피드백]
     * subnet 대역이 간격이 떨어져있도록 (나중에 키울 때 )
     */

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        if (errorCode.getStatus().is5xxServerError()) {
            log.error("[5xx Error]: ", e);
        }
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidationException(MethodArgumentNotValidException e) {
        return ErrorResponse.of(ErrorCode.INVALID_REQUEST);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleNotReadable(HttpMessageNotReadableException e) {
        return ErrorResponse.of(ErrorCode.INVALID_REQUEST);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ErrorResponse.of(ErrorCode.INVALID_REQUEST);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleMissingRequestParameter(MissingServletRequestParameterException e) {
        return ErrorResponse.of(ErrorCode.INVALID_REQUEST);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleHandlerMethodValidation(HandlerMethodValidationException e) {
        return ErrorResponse.of(ErrorCode.INVALID_REQUEST);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNoResourceFound(NoResourceFoundException e) {
        return ErrorResponse.of(ErrorCode.NOT_FOUND);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ErrorResponse handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return ErrorResponse.of(ErrorCode.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public ErrorResponse handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        return ErrorResponse.of(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException e) {
        ErrorCode errorCode = resolveErrorCode(e.getStatusCode());
        if (e.getStatusCode().is5xxServerError()) {
            log.error("[5xx Error]: ", e);
        }
        return ResponseEntity
                .status(e.getStatusCode())
                .body(ErrorResponse.of(errorCode));
    }

    private ErrorCode resolveErrorCode(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> ErrorCode.INVALID_REQUEST;
            case 401 -> ErrorCode.UNAUTHORIZED;
            case 403 -> ErrorCode.FORBIDDEN;
            case 404 -> ErrorCode.NOT_FOUND;
            case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
            default -> status.is5xxServerError() ? ErrorCode.INTERNAL_ERROR : ErrorCode.INVALID_REQUEST;
        };
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleUnexpected(Exception e) {
        log.error("[500 Error]: ", e);
        return ErrorResponse.of(ErrorCode.INTERNAL_ERROR);
    }
}
