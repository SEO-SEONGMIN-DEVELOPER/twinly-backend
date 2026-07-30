package com.nidus.twinly.people.dto.result;

import com.nidus.twinly.common.photo.ProfilePhotoInfo;
import com.nidus.twinly.relationship.domain.RelationshipSpecificType;
import com.nidus.twinly.relationship.domain.RelationshipType;

public record PeopleProfileResult(
        Long userId,
        String userName,
        ProfilePhotoInfo profilePhoto,
        Integer intimacy,
        RelationshipType relationshipType,
        RelationshipSpecificType relationshipSpecificType,
        Boolean isFavorited,
        PeopleProfileDisclosedFieldsResult disclosedFields,
        Boolean isDeleted,
        Boolean isBlocked
) {
}
