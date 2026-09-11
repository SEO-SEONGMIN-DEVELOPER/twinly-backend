package com.nidus.twinly.chat.dto.response;

import com.nidus.twinly.chat.dto.result.ChatCommonPointResult;

public record ChatCommonPointResponse(
        String message
) {

    public static ChatCommonPointResponse from(ChatCommonPointResult result) {
        return new ChatCommonPointResponse(result.message());
    }
}
