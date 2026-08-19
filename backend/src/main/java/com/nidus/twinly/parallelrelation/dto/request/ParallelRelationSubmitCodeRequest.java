package com.nidus.twinly.parallelrelation.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ParallelRelationSubmitCodeRequest(
        @NotBlank String code
) {
}
