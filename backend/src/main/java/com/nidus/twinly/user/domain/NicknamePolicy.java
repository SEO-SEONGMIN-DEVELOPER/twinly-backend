package com.nidus.twinly.user.domain;

import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;

import java.util.Set;
import java.util.regex.Pattern;

public final class NicknamePolicy {

    private static final Set<String> FORBIDDEN_WORDS = Set.of(
            "admin", "관리자", "운영자", "공지"
    );

    private static final Pattern PATTERN = Pattern.compile("^[가-힣a-zA-Z0-9_-]{2,20}$");

    private NicknamePolicy() {
    }

    public static String normalize(String nickname) {
        String trimmed = nickname.trim();

        if (!PATTERN.matcher(trimmed).matches()) {
            throw new BusinessException(ErrorCode.INVALID_NICKNAME, "닉네임은 2~20자의 한글·영문·숫자·(_-)만 사용할 수 있습니다: " + trimmed);
        }

        String lowered = trimmed.toLowerCase();
        boolean containsForbiddenWord = FORBIDDEN_WORDS.stream()
                .anyMatch(lowered::contains);

        if (containsForbiddenWord) {
            throw new BusinessException(ErrorCode.INVALID_NICKNAME);
        }

        return trimmed;
    }
}
