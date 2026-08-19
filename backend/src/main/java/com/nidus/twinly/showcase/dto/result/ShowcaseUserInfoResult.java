package com.nidus.twinly.showcase.dto.result;

import com.nidus.twinly.common.domain.Gender;

public record ShowcaseUserInfoResult(
        Long userRef,
        String userName,
        Gender gender,
        String organization
) {
}
