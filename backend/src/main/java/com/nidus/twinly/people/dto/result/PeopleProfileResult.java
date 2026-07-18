package com.nidus.twinly.people.dto.result;

import com.nidus.twinly.relationship.domain.RelationshipSpecificType;
import com.nidus.twinly.relationship.domain.RelationshipType;
import com.nidus.twinly.user.domain.AvatarPaletteColor;

public record PeopleProfileResult(
        Long userId,
        String userName,
        String profilePhotoUrl,
        AvatarPaletteColor avatarPaletteColor,
        Integer intimacy,
        RelationshipType relationshipType,
        RelationshipSpecificType relationshipSpecificType,
        Boolean isFavorited,
        Boolean isHighlighted,
        PeopleProfileDisclosedFieldsResult disclosedFields,
        Boolean isDeleted,
        Boolean isBlocked
) {
}
