package com.nidus.twinly.app.dto.response;

import com.nidus.twinly.common.web.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;

public record AppUpdateRequiredResponse(
        String code,
        @Schema(nullable = true) String message,
        String storeUrl,
        String minVersion
) {
    public static AppUpdateRequiredResponse of(String storeUrl, String minVersion) {
        return new AppUpdateRequiredResponse(ErrorCode.APP_UPDATE_REQUIRED.name(), ErrorCode.APP_UPDATE_REQUIRED.getDefaultMessage(), storeUrl, minVersion);
    }
}
