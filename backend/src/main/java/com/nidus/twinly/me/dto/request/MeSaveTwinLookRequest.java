package com.nidus.twinly.me.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record MeSaveTwinLookRequest(
        @NotNull @Min(1) @Max(99) Integer hair,
        @NotNull @Min(1) @Max(99) Integer hairColor,
        @NotNull @Min(1) @Max(99) Integer outfit,
        @NotNull @Min(1) @Max(99) Integer outfitColor
) {
}
