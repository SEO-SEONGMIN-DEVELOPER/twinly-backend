package com.nidus.twinly.schoolverification.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum StudentIdRejectReason {
    @JsonProperty("BLURRY") BLURRY("사진이 흐리거나 잘려서 확인할 수 없어요."),
    @JsonProperty("NAME_MISMATCH") NAME_MISMATCH("본인인증한 이름과 학생증 이름이 달라요."),
    @JsonProperty("SCHOOL_MISMATCH") SCHOOL_MISMATCH("선택한 학교의 학생증이 아니에요."),
    @JsonProperty("INVALID_DOCUMENT") INVALID_DOCUMENT("학생증이나 재학증명서로 확인할 수 없는 사진이에요."),
    @JsonProperty("RRN_VISIBLE") RRN_VISIBLE("주민등록번호 뒷자리가 보여요. 가리고 다시 올려 주세요.");

    private final String message;

    StudentIdRejectReason(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
