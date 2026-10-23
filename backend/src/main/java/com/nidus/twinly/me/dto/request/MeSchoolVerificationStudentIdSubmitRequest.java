package com.nidus.twinly.me.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MeSchoolVerificationStudentIdSubmitRequest(
        @NotBlank @Size(max = 255) String key
) {
}
