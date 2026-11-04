package com.nidus.twinly.me.dto.result;

import com.nidus.twinly.twinlook.entity.TwinLook;

public record MeTwinLookPartResult(
        Integer hair,
        Integer hairColor,
        Integer outfit,
        Integer outfitColor
) {

    public static MeTwinLookPartResult from(TwinLook twinLook) {
        return new MeTwinLookPartResult(
                twinLook.getHair(),
                twinLook.getHairColor(),
                twinLook.getOutfit(),
                twinLook.getOutfitColor()
        );
    }
}
