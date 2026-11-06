package com.nidus.twinly.auth.dto.result;

import com.nidus.twinly.organization.domain.OrganizationType;

public record AuthOnboardingEmailVerifyResult(
        OrganizationType organizationType,
        String organizationName
) {
}
