package com.nidus.twinly.common.web;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    // 공통
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 서버 오류가 발생했습니다."),

    // 인증/토큰
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다."),
    WITHDRAWN_USER(HttpStatus.UNAUTHORIZED, "탈퇴한 유저입니다."),
    INVALID_ANON_SESSION(HttpStatus.UNAUTHORIZED, "유효하지 않은 세션입니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 리프레시 토큰입니다."),
    REFRESH_TOKEN_ALREADY_REVOKED(HttpStatus.UNAUTHORIZED, "이미 무효화된 리프레시 토큰입니다."),

    // 인증(회원가입/인증번호)
    EMAIL_NOT_REGISTERED(HttpStatus.NOT_FOUND, "가입되지 않은 이메일입니다."),
    PHONE_NOT_REGISTERED(HttpStatus.NOT_FOUND, "가입되지 않은 전화번호입니다."),
    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    PHONE_ALREADY_REGISTERED(HttpStatus.CONFLICT, "이미 가입된 전화번호입니다."),
    VERIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "유효하지 않은 인증 요청입니다."),
    SIGNUP_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "유효하지 않은 세션입니다."),
    VERIFICATION_CODE_EXPIRED(HttpStatus.GONE, "인증번호가 만료되었습니다."),
    VERIFICATION_EXPIRED(HttpStatus.GONE, "인증이 만료되었습니다."),
    VERIFICATION_CODE_MISMATCH(HttpStatus.UNPROCESSABLE_CONTENT, "인증번호가 일치하지 않습니다."),
    VERIFICATION_NOT_COMPLETED(HttpStatus.UNPROCESSABLE_CONTENT, "인증이 완료되지 않았습니다."),

    // 유저/회원
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 유저입니다."),
    WITHDRAWAL_ALREADY_REQUESTED(HttpStatus.CONFLICT, "이미 탈퇴 신청이 된 상태입니다."),
    WITHDRAWAL_RECOVERY_EXPIRED(HttpStatus.UNPROCESSABLE_CONTENT, "복구 가능 기간이 지났습니다."),

    // 온보딩/닉네임/설문
    NICKNAME_ALREADY_USED(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    INVALID_NICKNAME(HttpStatus.UNPROCESSABLE_CONTENT, "사용할 수 없는 닉네임입니다."),
    SURVEY_QUESTION_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 질문입니다."),

    // 동의(정책)
    POLICY_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 정책 또는 버전입니다."),
    REQUIRED_POLICY_REVOKE_DENIED(HttpStatus.FORBIDDEN, "필수 정책은 철회할 수 없습니다."),

    // 알림
    APP_NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 알림입니다."),

    // 망설임(hesitation)
    HESITATION_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 망설임입니다."),
    NOT_HESITATION_OWNER(HttpStatus.FORBIDDEN, "본인의 망설임이 아닙니다."),
    HESITATION_ALREADY_HANDLED(HttpStatus.CONFLICT, "이미 처리된 망설임입니다."),
    HESITATION_ANSWER_EMPTY(HttpStatus.UNPROCESSABLE_CONTENT, "답변 내용이 없습니다."),
    HESITATION_ANSWER_NOT_IN_OPTIONS(HttpStatus.UNPROCESSABLE_CONTENT, "선택지에 없는 답변입니다."),

    // 채팅
    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 채팅방입니다."),
    MATCH_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 매칭입니다."),
    CHAT_PARTICIPATION_NOT_FOUND(HttpStatus.NOT_FOUND, "참여 정보가 없습니다."),
    NOT_MATCH_PARTICIPANT(HttpStatus.FORBIDDEN, "이 매칭의 참여자가 아닙니다."),
    NOT_ACTIVE_ROOM_PARTICIPANT(HttpStatus.FORBIDDEN, "더 이상 참여 중이지 않은 채팅방입니다."),
    MESSAGE_LENGTH_EXCEEDED(HttpStatus.UNPROCESSABLE_CONTENT, "메시지 길이 상한을 초과했습니다."),
    MESSAGE_NOT_IN_ROOM(HttpStatus.UNPROCESSABLE_CONTENT, "해당 방에 존재하지 않는 메시지입니다."),
    CLIENT_MSG_ID_CONFLICT(HttpStatus.CONFLICT, "이미 다른 내용으로 사용된 clientMsgId입니다."),

    // 관계/사람
    ENCOUNTER_NOT_FOUND(HttpStatus.NOT_FOUND, "만난 적 없는 상대입니다."),
    RELATIONSHIP_NOT_FOUND(HttpStatus.NOT_FOUND, "관계 없는 상대입니다."),

    // AI 채팅
    AI_QUESTION_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 턴의 AI 질문이 존재하지 않습니다."),

    // 신고
    CANNOT_REPORT_SELF(HttpStatus.UNPROCESSABLE_CONTENT, "자기 자신을 신고할 수 없습니다."),
    SCENE_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 씬입니다."),
    SCENE_TARGET_MISMATCH(HttpStatus.UNPROCESSABLE_CONTENT, "대상과 씬이 일치하지 않습니다."),

    // 차단
    CANNOT_BLOCK_SELF(HttpStatus.UNPROCESSABLE_CONTENT, "자기 자신을 차단할 수 없습니다."),

    // 시즌
    SEASON_NOT_JOINABLE(HttpStatus.UNPROCESSABLE_CONTENT, "지금은 시즌 참가 기간이 아닙니다."),

    // 사진/업로드
    NOT_KEY_OWNER(HttpStatus.FORBIDDEN, "본인 소유의 key가 아닙니다."),
    UPLOAD_NOT_COMPLETED(HttpStatus.UNPROCESSABLE_CONTENT, "업로드가 완료되지 않은 key입니다."),
    UNSUPPORTED_IMAGE_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 이미지 형식입니다."),

    // 외부 연동
    EMAIL_SEND_FAILED(HttpStatus.BAD_GATEWAY, "이메일 발송에 실패했습니다."),
    SMS_SEND_FAILED(HttpStatus.BAD_GATEWAY, "SMS 발송에 실패했습니다.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
