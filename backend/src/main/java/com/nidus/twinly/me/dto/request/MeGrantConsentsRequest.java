package com.nidus.twinly.me.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record MeGrantConsentsRequest(
        @NotNull @Valid List<MeGrantConsentsItemRequest> grants
) {
}
