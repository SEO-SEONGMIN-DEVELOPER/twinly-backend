package com.nidus.twinly.app.dto.request;

import com.nidus.twinly.app.domain.AppVersion;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record AppVersionPolicyUpdateRequest(
        @NotNull
        @Schema(type = "string", example = "0.2.0")
        AppVersion minVersion,
        @NotBlank
        @Pattern(regexp = "^https://\\S+$")
        String storeUrl
) {
}
