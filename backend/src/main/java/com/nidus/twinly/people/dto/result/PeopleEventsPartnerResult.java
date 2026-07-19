package com.nidus.twinly.people.dto.result;

import com.nidus.twinly.relationship.domain.RelationshipSpecificType;
import com.nidus.twinly.user.domain.AvatarPaletteColor;

public record PeopleEventsPartnerResult(
        Long userId,
        String userName,
        String profilePhotoUrl,
        AvatarPaletteColor avatarPaletteColor,
        Integer intimacy,
        RelationshipSpecificType relationshipSpecificType
) {
}
