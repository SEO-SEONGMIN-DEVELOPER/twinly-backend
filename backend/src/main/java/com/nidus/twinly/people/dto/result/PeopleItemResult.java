package com.nidus.twinly.people.dto.result;

import com.nidus.twinly.relationship.domain.RelationshipSpecificType;
import com.nidus.twinly.relationship.domain.RelationshipType;

public record PeopleItemResult(
        Long userId,
        String userName,
        String profilePhotoUrl,
        Integer intimacy,
        RelationshipType relationshipType,
        RelationshipSpecificType relationshipSpecificType,
        Integer sceneElementCount,
        Long chatRoomId,
        Boolean isFavorited,
        Boolean isHighlighted
) {
}
