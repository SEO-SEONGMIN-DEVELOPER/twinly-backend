package com.nidus.twinly.me.dto.response;

import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.me.dto.result.MeInfoResult;

public record MeInfoResponse(
        Gender gender
) {

    public static MeInfoResponse from(MeInfoResult result) {
        return new MeInfoResponse(result.gender());
    }
}
