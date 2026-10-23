package com.nidus.twinly.auth.dto.command;

import com.nidus.twinly.auth.dto.request.AuthOnboardingStudentIdSubmitRequest;

public record AuthOnboardingStudentIdSubmitCommand(
        String organizationName,
        String key
) {

    public static AuthOnboardingStudentIdSubmitCommand from(AuthOnboardingStudentIdSubmitRequest request) {
        return new AuthOnboardingStudentIdSubmitCommand(request.organizationName().trim(), request.key());
    }
}
