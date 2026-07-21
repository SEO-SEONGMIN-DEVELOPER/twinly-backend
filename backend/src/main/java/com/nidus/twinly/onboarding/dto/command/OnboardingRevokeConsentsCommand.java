package com.nidus.twinly.onboarding.dto.command;

import com.nidus.twinly.onboarding.dto.request.OnboardingRevokeConsentsRequest;

import java.util.List;

public record OnboardingRevokeConsentsCommand(
        List<OnboardingRevokeConsentsItemCommand> grants
) {

    public static OnboardingRevokeConsentsCommand from(OnboardingRevokeConsentsRequest request) {
        return new OnboardingRevokeConsentsCommand(
                request.grants().stream().map(OnboardingRevokeConsentsItemCommand::from).toList()
        );
    }
}
