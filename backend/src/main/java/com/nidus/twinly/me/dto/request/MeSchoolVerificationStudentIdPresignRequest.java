package com.nidus.twinly.me.dto.request;

import jakarta.validation.constraints.NotBlank;

public record MeSchoolVerificationStudentIdPresignRequest(
        @NotBlank String contentType
) {
}
