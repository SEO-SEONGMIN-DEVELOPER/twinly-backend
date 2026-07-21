package com.nidus.twinly.me.dto.response;

import com.nidus.twinly.me.dto.result.MeConsentsResult;

import java.util.List;

public record MeConsentsResponse(
        List<MeConsentsItemResponse> consents
) {

    public static MeConsentsResponse from(MeConsentsResult result) {
        return new MeConsentsResponse(
                result.consents().stream().map(MeConsentsItemResponse::from).toList()
        );
    }
}
