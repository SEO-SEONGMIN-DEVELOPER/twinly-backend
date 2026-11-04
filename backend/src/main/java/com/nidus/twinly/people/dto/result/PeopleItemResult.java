package com.nidus.twinly.people.dto.result;

import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.photo.ProfilePhotoInfo;
import com.nidus.twinly.relationship.domain.RelationshipSpecificType;
import com.nidus.twinly.relationship.domain.RelationshipType;

public record PeopleItemResult(
        Long userId,
        String userName,
        Gender gender,
        ProfilePhotoInfo profilePhoto,
        Integer intimacy,
        Integer gameIntimacy,
        RelationshipType relationshipType,
        RelationshipSpecificType relationshipSpecificType,
        Integer sceneElementCount,
        Long chatRoomId,
        Boolean isFavorited,
        PeopleIntimacyQuizStatusResult intimacyQuizStatus
) {
}
