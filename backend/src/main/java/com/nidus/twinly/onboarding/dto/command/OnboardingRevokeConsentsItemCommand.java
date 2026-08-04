package com.nidus.twinly.onboarding.dto.command;

import com.nidus.twinly.onboarding.dto.request.OnboardingRevokeConsentsItemRequest;

public record OnboardingRevokeConsentsItemCommand(
        String policyId,
        String version
) {

    public static OnboardingRevokeConsentsItemCommand from(OnboardingRevokeConsentsItemRequest request) {
        return new OnboardingRevokeConsentsItemCommand(request.policyId(), request.version());
    }
}
