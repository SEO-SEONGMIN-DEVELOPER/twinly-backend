package com.nidus.twinly.auth.dto.command;

import com.nidus.twinly.auth.dto.request.AuthOnboardingStudentIdPresignRequest;

public record AuthOnboardingStudentIdPresignCommand(
        String contentType
) {

    public static AuthOnboardingStudentIdPresignCommand from(AuthOnboardingStudentIdPresignRequest request) {
        return new AuthOnboardingStudentIdPresignCommand(request.contentType());
    }
}
