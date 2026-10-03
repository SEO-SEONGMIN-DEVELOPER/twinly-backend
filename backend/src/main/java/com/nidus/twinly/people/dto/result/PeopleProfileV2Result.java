package com.nidus.twinly.people.dto.result;

import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.photo.ProfilePhotoInfo;
import com.nidus.twinly.relationship.domain.RelationshipSpecificType;
import com.nidus.twinly.relationship.domain.RelationshipType;

public record PeopleProfileV2Result(
        Long userId,
        String userName,
        Gender gender,
        String organization,
        String birthYear,
        ProfilePhotoInfo profilePhoto,
        Integer intimacy,
        Integer gameIntimacy,
        RelationshipType relationshipType,
        RelationshipSpecificType relationshipSpecificType,
        Boolean isFavorited,
        PeopleProfileDisclosedFieldsResult disclosedFields,
        Boolean isDeleted,
        Boolean isBlocked
) {
}
