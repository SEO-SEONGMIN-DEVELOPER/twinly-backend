package com.nidus.twinly.people.dto.response;

import com.nidus.twinly.people.dto.result.PeopleItemResult;
import com.nidus.twinly.relationship.domain.RelationshipSpecificType;
import com.nidus.twinly.relationship.domain.RelationshipType;
import com.nidus.twinly.user.domain.AvatarPaletteColor;

public record PeopleItemResponse(
        Long userId,
        String userName,
        String profilePhotoUrl,
        AvatarPaletteColor avatarPaletteColor,
        Integer intimacy,
        RelationshipType relationshipType,
        RelationshipSpecificType relationshipSpecificType,
        Integer sceneElementCount,
        Long chatRoomId,
        Boolean isFavorited,
        Boolean isHighlighted
) {

    public static PeopleItemResponse from(PeopleItemResult result) {
        return new PeopleItemResponse(
                result.userId(),
                result.userName(),
                result.profilePhotoUrl(),
                result.avatarPaletteColor(),
                result.intimacy(),
                result.relationshipType(),
                result.relationshipSpecificType(),
                result.sceneElementCount(),
                result.chatRoomId(),
                result.isFavorited(),
                result.isHighlighted()
        );
    }
}
