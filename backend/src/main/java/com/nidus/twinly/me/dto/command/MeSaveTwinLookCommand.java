package com.nidus.twinly.me.dto.command;

import com.nidus.twinly.me.dto.request.MeSaveTwinLookRequest;

public record MeSaveTwinLookCommand(
        Integer hair,
        Integer hairColor,
        Integer outfit,
        Integer outfitColor
) {

    public static MeSaveTwinLookCommand from(MeSaveTwinLookRequest request) {
        return new MeSaveTwinLookCommand(
                request.hair(),
                request.hairColor(),
                request.outfit(),
                request.outfitColor()
        );
    }
}
