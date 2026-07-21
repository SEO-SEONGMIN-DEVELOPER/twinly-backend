package com.nidus.twinly.common.photo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record PhotoPosInfo(
        @NotNull @Valid StartPos startPos,
        @NotNull Integer width,
        @NotNull Integer height
) {
    public record StartPos(@NotNull Integer x, @NotNull Integer y) {}
}