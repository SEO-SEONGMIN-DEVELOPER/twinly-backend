package com.nidus.twinly.showcase.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.showcase.dto.result.ShowcaseUserInfoResult;

public record ShowcaseUserInfoResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Long userRef,
        String userName,
        Gender gender,
        String organization
) {

    public static ShowcaseUserInfoResponse from(ShowcaseUserInfoResult result) {
        return new ShowcaseUserInfoResponse(result.userRef(), result.userName(), result.gender(), result.organization());
    }
}
