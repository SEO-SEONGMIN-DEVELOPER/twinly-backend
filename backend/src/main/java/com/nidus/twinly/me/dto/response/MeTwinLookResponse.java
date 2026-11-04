package com.nidus.twinly.me.dto.response;

import com.nidus.twinly.me.dto.result.MeTwinLookResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record MeTwinLookResponse(
        @Schema(nullable = true)
        MeTwinLookPartResponse look
) {

    public static MeTwinLookResponse from(MeTwinLookResult result) {
        return new MeTwinLookResponse(result.look() == null ? null : MeTwinLookPartResponse.from(result.look()));
    }
}
