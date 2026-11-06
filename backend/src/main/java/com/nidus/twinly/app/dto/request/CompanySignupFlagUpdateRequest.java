package com.nidus.twinly.app.dto.request;

import jakarta.validation.constraints.NotNull;

public record CompanySignupFlagUpdateRequest(
        @NotNull Boolean enabled
) {
}
