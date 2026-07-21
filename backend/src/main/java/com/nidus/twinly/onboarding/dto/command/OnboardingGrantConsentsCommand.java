package com.nidus.twinly.onboarding.dto.command;

import com.nidus.twinly.onboarding.dto.request.OnboardingGrantConsentsRequest;

import java.util.List;

public record OnboardingGrantConsentsCommand(
        List<OnboardingGrantConsentsItemCommand> grants
) {

    public static OnboardingGrantConsentsCommand from(OnboardingGrantConsentsRequest request) {
        return new OnboardingGrantConsentsCommand(
                request.grants().stream().map(OnboardingGrantConsentsItemCommand::from).toList()
        );
    }
}
