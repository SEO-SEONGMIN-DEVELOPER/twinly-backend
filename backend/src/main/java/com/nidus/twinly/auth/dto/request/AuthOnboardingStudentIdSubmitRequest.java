package com.nidus.twinly.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthOnboardingStudentIdSubmitRequest(
        @NotBlank @Size(max = 100) String organizationName,
        @NotBlank @Size(max = 255) String key
) {
}
