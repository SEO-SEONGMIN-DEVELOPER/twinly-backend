package com.nidus.twinly.auth.dto.response;

import com.nidus.twinly.auth.dto.result.AuthOnboardingEmailVerifyResult;
import com.nidus.twinly.organization.domain.OrganizationType;
import io.swagger.v3.oas.annotations.media.Schema;

public record AuthOnboardingEmailVerifyV2Response(
        OrganizationType organizationType,
        @Schema(nullable = true)
        String organizationName
) {

    public static AuthOnboardingEmailVerifyV2Response from(AuthOnboardingEmailVerifyResult result) {
        return new AuthOnboardingEmailVerifyV2Response(result.organizationType(), result.organizationName());
    }
}
