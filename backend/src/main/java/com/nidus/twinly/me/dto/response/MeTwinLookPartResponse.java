package com.nidus.twinly.me.dto.response;

import com.nidus.twinly.me.dto.result.MeTwinLookPartResult;

public record MeTwinLookPartResponse(
        Integer hair,
        Integer hairColor,
        Integer outfit,
        Integer outfitColor
) {

    public static MeTwinLookPartResponse from(MeTwinLookPartResult result) {
        return new MeTwinLookPartResponse(
                result.hair(),
                result.hairColor(),
                result.outfit(),
                result.outfitColor()
        );
    }
}
