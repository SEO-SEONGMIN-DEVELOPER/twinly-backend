package com.nidus.twinly.onboarding.dto.command;

import com.nidus.twinly.onboarding.dto.request.OnboardingGrantConsentsItemRequest;

public record OnboardingGrantConsentsItemCommand(
        Long policyId,
        Integer version
) {

    public static OnboardingGrantConsentsItemCommand from(OnboardingGrantConsentsItemRequest request) {
        return new OnboardingGrantConsentsItemCommand(request.policyId(), request.version());
    }
}
