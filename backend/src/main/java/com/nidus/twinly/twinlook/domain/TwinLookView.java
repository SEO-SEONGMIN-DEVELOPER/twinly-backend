package com.nidus.twinly.twinlook.domain;

import com.nidus.twinly.twinlook.entity.TwinLook;

public record TwinLookView(
        int hair,
        int hairColor,
        int outfit,
        int outfitColor
) {

    public static TwinLookView from(TwinLook twinLook) {
        return new TwinLookView(
                twinLook.getHair(),
                twinLook.getHairColor(),
                twinLook.getOutfit(),
                twinLook.getOutfitColor()
        );
    }
}
