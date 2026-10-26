package com.nidus.twinly.me.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record MeSaveTwinUpdateInterestsRequest(
        @NotNull UUID updateId,
        @NotNull @Size(min = 1, max = 50) List<@NotBlank @Size(max = 50) String> interests
) {
}
