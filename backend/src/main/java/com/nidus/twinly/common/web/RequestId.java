package com.nidus.twinly.common.web;

public final class RequestId {

    private RequestId() {
    }

    public static Long toLong(String raw, String fieldName) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, fieldName + " 형식이 올바르지 않습니다.");
        }
    }

    public static Long toLongOrNull(String raw, String fieldName) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return toLong(raw, fieldName);
    }
}
